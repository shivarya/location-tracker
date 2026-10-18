package com.shivarya.locationspeedtracker.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.shivarya.locationspeedtracker.core.settings.LocationSourceMode
import com.shivarya.locationspeedtracker.core.tracking.Fix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

object LocationPermissions {
    fun hasFine(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasAny(context: Context) = hasFine(context) ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }
}

/** A stream of fixes at roughly [intervalMs]. Cold: collection starts updates, cancellation stops them. */
interface LocationSource {
    fun updates(intervalMs: Long): Flow<Fix>
}

/** Turns platform Locations into [Fix]es, adding mean-sea-level altitude where the device can compute it. */
@Singleton
class FixMapper @Inject constructor(@param:ApplicationContext private val context: Context) {
    @Volatile private var mslUnavailable = false

    /** Blocking (the geoid model is read from disk on first use) — call off the main thread. */
    fun map(location: Location): Fix {
        val msl = msl(location)
        return Fix(
            time = location.time,
            lat = location.latitude,
            lon = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            altitudeMsl = msl,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
            hAcc = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            vAcc = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else null,
            speedAcc = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else null,
            bearingAcc = if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees.toDouble() else null,
            provider = location.provider,
            satellites = location.extras?.getInt("satellites", -1)?.takeIf { it >= 0 },
            isMock = if (Build.VERSION.SDK_INT >= 31) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider,
            elapsedNanos = location.elapsedRealtimeNanos,
        )
    }

    private fun msl(location: Location): Double? {
        if (!location.hasAltitude()) return null
        if (LocationCompat.hasMslAltitude(location)) return LocationCompat.getMslAltitudeMeters(location)
        if (mslUnavailable) return null
        return try {
            val copy = Location(location)
            // Geoid model (EGM2008 tiles) bundled in the library: WGS84 ellipsoid height → mean sea level.
            AltitudeConverterCompat.addMslAltitudeToLocation(context, copy)
            if (LocationCompat.hasMslAltitude(copy)) LocationCompat.getMslAltitudeMeters(copy) else null
        } catch (e: Exception) {
            Log.w("FixMapper", "MSL altitude unavailable; using ellipsoid height", e)
            mslUnavailable = true
            null
        }
    }
}

/** Google Play services fused provider: GNSS + Wi-Fi + Bluetooth + cell, Kalman-smoothed by the platform. */
@Singleton
class FusedLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mapper: FixMapper,
) : LocationSource {
    private val client: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override fun updates(intervalMs: Long): Flow<Fix> = callbackFlow {
        if (!LocationPermissions.hasAny(context)) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(200))
            .setMaxUpdateDelayMillis(0)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose { client.removeLocationUpdates(callback) }
    }.buffer(64).map { mapper.map(it) }.flowOn(Dispatchers.Default)
}

/** The raw GNSS chip through LocationManager — no network assistance; also the highest-rate source (perf timer). */
@Singleton
class GnssLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mapper: FixMapper,
) : LocationSource {
    @SuppressLint("MissingPermission")
    override fun updates(intervalMs: Long): Flow<Fix> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm == null || !LocationPermissions.hasFine(context)) {
            close(SecurityException("Precise location permission not granted"))
            return@callbackFlow
        }
        val listener = LocationListenerCompat { trySend(it) }
        val executor = Executors.newSingleThreadExecutor()
        val request = LocationRequestCompat.Builder(intervalMs)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(intervalMs)
            .build()
        LocationManagerCompat.requestLocationUpdates(lm, LocationManager.GPS_PROVIDER, request, executor, listener)
        awaitClose {
            LocationManagerCompat.removeUpdates(lm, listener)
            executor.shutdown()
        }
    }.buffer(64).map { mapper.map(it) }.flowOn(Dispatchers.Default)
}

/** Resolves the user's chosen source. Debug builds can swap in a replayed GPX track (see ReplayLocationSource). */
@Singleton
class LocationSources @Inject constructor(
    val fused: FusedLocationSource,
    val gnss: GnssLocationSource,
) {
    @Volatile var override: LocationSource? = null

    fun forMode(mode: LocationSourceMode): LocationSource = override ?: when (mode) {
        LocationSourceMode.Fused -> fused
        LocationSourceMode.Gnss -> gnss
    }
}
