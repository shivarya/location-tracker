package com.shivarya.locationspeedtracker.core.sync

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.shivarya.locationspeedtracker.core.geo.Geo
import com.shivarya.locationspeedtracker.core.model.ActivityType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The parts of a geocoded address a journey name is made from. */
data class Place(val locality: String? = null, val subLocality: String? = null, val adminArea: String? = null)

/** Names a journey after where it went, through the platform [Geocoder] (needs a network; Play services' backend). */
@Singleton
class PlaceNamer @Inject constructor(@param:ApplicationContext private val context: Context) {
    val available: Boolean get() = Geocoder.isPresent()

    /** Null when nothing is known there (open sea); throws [IOException] when the lookup itself failed (offline). */
    suspend fun place(lat: Double, lon: Double): Place? {
        val geocoder = Geocoder(context)
        val address: Address? = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { c ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = c.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = c.resumeWithException(IOException(errorMessage ?: "Geocoder failed"))
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
        }
        return address?.let { Place(it.locality, it.subLocality, it.adminArea) }?.takeIf { it.locality ?: it.subLocality ?: it.adminArea != null }
    }

    companion object {
        /** Start and end closer than this are a loop: named after one place. */
        private const val LOOP_M = 2_000.0
        private const val MAX_LENGTH = 60

        /**
         * "Bengaluru → Delhi flight" between towns, "Indiranagar → Koramangala drive" across one town, "Indiranagar
         * run" for a loop. General journeys are "trips" when named after one place and get no noun on a route.
         */
        fun routeName(
            start: Place?,
            end: Place?,
            activity: ActivityType,
            startLatLon: Pair<Double, Double>,
            endLatLon: Pair<Double, Double>,
        ): String? {
            val a = start ?: end ?: return null
            val noun = noun(activity)
            val loop = end == null || start == null ||
                Geo.distance(startLatLon.first, startLatLon.second, endLatLon.first, endLatLon.second) < LOOP_M
            val name = if (loop) {
                "${a.subLocality ?: a.locality ?: a.adminArea} ${noun ?: "trip"}"
            } else {
                val (from, to) = when {
                    start.locality != null && end.locality != null && start.locality != end.locality -> start.locality to end.locality
                    start.subLocality != null && end.subLocality != null && start.subLocality != end.subLocality -> start.subLocality to end.subLocality
                    start.locality == null && end.locality == null && start.adminArea != end.adminArea -> start.adminArea to end.adminArea
                    else -> null to null
                }
                if (from == null || to == null) "${a.locality ?: a.subLocality ?: a.adminArea} ${noun ?: "trip"}"
                else "$from → $to" + (noun?.let { " $it" } ?: "")
            }
            return name.take(MAX_LENGTH).trim()
        }

        private fun noun(a: ActivityType): String? = when (a) {
            ActivityType.Walk -> "walk"
            ActivityType.Run -> "run"
            ActivityType.Hike -> "hike"
            ActivityType.Cycle, ActivityType.Motorbike -> "ride"
            ActivityType.Drive -> "drive"
            ActivityType.Train -> "train"
            ActivityType.Flight -> "flight"
            ActivityType.Other -> null
        }
    }
}
