package com.shivarya.locationspeedtracker.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import com.shivarya.locationspeedtracker.core.location.Constellation.Companion.toConstellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class Constellation(val label: String, val flag: String) {
    Gps("GPS", "US"), Glonass("GLONASS", "RU"), Galileo("Galileo", "EU"), Beidou("BeiDou", "CN"),
    Qzss("QZSS", "JP"), Navic("NavIC", "IN"), Sbas("SBAS", "Aug"), Unknown("Other", "?");

    companion object {
        fun Int.toConstellation() = when (this) {
            GnssStatusCompat.CONSTELLATION_GPS -> Gps
            GnssStatusCompat.CONSTELLATION_GLONASS -> Glonass
            GnssStatusCompat.CONSTELLATION_GALILEO -> Galileo
            GnssStatusCompat.CONSTELLATION_BEIDOU -> Beidou
            GnssStatusCompat.CONSTELLATION_QZSS -> Qzss
            GnssStatusCompat.CONSTELLATION_IRNSS -> Navic
            GnssStatusCompat.CONSTELLATION_SBAS -> Sbas
            else -> Unknown
        }
    }
}

data class Satellite(
    val svid: Int,
    val constellation: Constellation,
    /** Carrier-to-noise density, dB-Hz. ~20 weak, 30 fair, 40+ strong. */
    val cn0: Float,
    val elevation: Float,
    val azimuth: Float,
    val usedInFix: Boolean,
    val carrierHz: Float?,
    val hasEphemeris: Boolean,
) {
    /** L5 / E5a / B2a band (1176.45 MHz): the second frequency that makes dual-frequency fixes possible. */
    val isL5: Boolean get() = carrierHz != null && abs(carrierHz - 1_176_450_000f) < 2_000_000f
}

data class Dop(val pdop: Double?, val hdop: Double?, val vdop: Double?)

data class GnssSnapshot(
    val satellites: List<Satellite> = emptyList(),
    val ttffMs: Int? = null,
    val dop: Dop? = null,
    val running: Boolean = false,
) {
    val used get() = satellites.count { it.usedInFix }
    val visible get() = satellites.size
    val dualFrequency get() = satellites.any { it.usedInFix && it.isL5 }
    val constellationsUsed get() = satellites.filter { it.usedInFix }.map { it.constellation }.distinct()

    /** Mean C/N0 of the four strongest satellites in the fix — the usual "signal strength" figure. */
    val topCn0: Float?
        get() = satellites.filter { it.usedInFix }.map { it.cn0 }.sortedDescending().take(4).takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

/** NMEA sentences we use: GSA (dilution of precision). */
object Nmea {
    /** `$GPGSA,A,3,04,05,,,,,,,,,,,1.8,1.0,1.5*33` — PDOP, HDOP, VDOP are fields 15–17. */
    fun parseGsa(sentence: String): Dop? {
        val s = sentence.trim()
        if (s.length < 7 || s[0] != '$' || !s.regionMatches(3, "GSA", 0, 3)) return null
        val body = s.substringBefore('*')
        val f = body.split(',')
        if (f.size < 18) return null
        fun d(i: Int) = f.getOrNull(i)?.toDoubleOrNull()?.takeIf { it > 0 && it < 99 }
        val dop = Dop(d(15), d(16), d(17))
        return if (dop.pdop == null && dop.hdop == null && dop.vdop == null) null else dop
    }
}

/**
 * Satellite status straight from the GNSS chip. Callbacks only flow while the GNSS engine is running, i.e. while
 * something is requesting location — the GPS doctor runs its own location request alongside.
 */
@Singleton
class GnssMonitor @Inject constructor(@param:ApplicationContext private val context: Context) {

    @SuppressLint("MissingPermission")
    fun status(): Flow<GnssSnapshot> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm == null || !LocationPermissions.hasFine(context)) {
            close(SecurityException("Precise location permission not granted"))
            return@callbackFlow
        }
        var snapshot = GnssSnapshot()
        val executor = Executors.newSingleThreadExecutor()
        val statusCallback = object : GnssStatusCompat.Callback() {
            override fun onStarted() {
                snapshot = snapshot.copy(running = true)
                trySend(snapshot)
            }

            override fun onStopped() {
                snapshot = snapshot.copy(running = false)
                trySend(snapshot)
            }

            override fun onFirstFix(ttffMillis: Int) {
                snapshot = snapshot.copy(ttffMs = ttffMillis)
                trySend(snapshot)
            }

            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                val sats = (0 until status.satelliteCount).map { i ->
                    Satellite(
                        svid = status.getSvid(i),
                        constellation = status.getConstellationType(i).toConstellation(),
                        cn0 = status.getCn0DbHz(i),
                        elevation = status.getElevationDegrees(i),
                        azimuth = status.getAzimuthDegrees(i),
                        usedInFix = status.usedInFix(i),
                        carrierHz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) else null,
                        hasEphemeris = status.hasEphemerisData(i),
                    )
                }
                snapshot = snapshot.copy(satellites = sats, running = true)
                trySend(snapshot)
            }
        }
        val nmea = OnNmeaMessageListener { message, _ ->
            Nmea.parseGsa(message)?.let {
                snapshot = snapshot.copy(dop = it)
                trySend(snapshot)
            }
        }
        LocationManagerCompat.registerGnssStatusCallback(lm, executor, statusCallback)
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) lm.addNmeaListener(executor, nmea)
            else @Suppress("DEPRECATION") lm.addNmeaListener(nmea, Handler(Looper.getMainLooper()))
        }
        awaitClose {
            LocationManagerCompat.unregisterGnssStatusCallback(lm, statusCallback)
            runCatching { lm.removeNmeaListener(nmea) }
            executor.shutdown()
        }
    }.conflate()
}
