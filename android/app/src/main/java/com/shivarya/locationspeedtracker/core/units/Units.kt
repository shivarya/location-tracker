package com.shivarya.locationspeedtracker.core.units

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Stored values match the Expo app's `unit_preference` strings, so migrated values need no mapping. */
enum class UnitSystem(val stored: String, val label: String) {
    Metric("metric", "Metric"),
    Imperial("imperial", "Imperial");

    companion object {
        fun from(stored: String?): UnitSystem = entries.firstOrNull { it.stored == stored } ?: Metric
    }
}

/** A number already converted to the display unit, plus that unit's label. */
data class Measure(val value: Double, val unit: String) {
    fun format(decimals: Int): String = "${value.fmt(decimals)} $unit"
}

/** All unit conversion lives here; everything stored is SI (m, m/s, s, hPa, kg). */
object Units {
    const val MPS_TO_KMH = 3.6
    const val MPS_TO_MPH = 2.2369362920544
    const val M_PER_KM = 1000.0
    const val M_PER_MILE = 1609.344
    const val FT_PER_M = 3.280839895
    const val INHG_PER_HPA = 0.0295299830714
    const val LB_PER_KG = 2.20462262185
    const val CM_PER_IN = 2.54

    fun speed(mps: Double, system: UnitSystem): Measure = when (system) {
        UnitSystem.Metric -> Measure(mps * MPS_TO_KMH, "km/h")
        UnitSystem.Imperial -> Measure(mps * MPS_TO_MPH, "mph")
    }

    fun speedUnit(system: UnitSystem): String = speed(0.0, system).unit

    /** Speed in the display unit back to m/s (performance-timer targets, speed-gauge scales). */
    fun speedToMps(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.Metric -> value / MPS_TO_KMH
        UnitSystem.Imperial -> value / MPS_TO_MPH
    }

    /** Long distances: km or mi. */
    fun distance(m: Double, system: UnitSystem): Measure = when (system) {
        UnitSystem.Metric -> Measure(m / M_PER_KM, "km")
        UnitSystem.Imperial -> Measure(m / M_PER_MILE, "mi")
    }

    /** Short distances (braking distance, accuracy radius): m or ft. */
    fun shortDistance(m: Double, system: UnitSystem): Measure = when (system) {
        UnitSystem.Metric -> Measure(m, "m")
        UnitSystem.Imperial -> Measure(m * FT_PER_M, "ft")
    }

    fun altitude(m: Double, system: UnitSystem): Measure = shortDistance(m, system)

    /** Vertical speed: m/min or ft/min. */
    fun verticalSpeed(mps: Double, system: UnitSystem): Measure = when (system) {
        UnitSystem.Metric -> Measure(mps * 60.0, "m/min")
        UnitSystem.Imperial -> Measure(mps * 60.0 * FT_PER_M, "ft/min")
    }

    /** Split length for automatic laps: 1 km or 1 mi. */
    fun splitLengthM(system: UnitSystem): Double = when (system) {
        UnitSystem.Metric -> M_PER_KM
        UnitSystem.Imperial -> M_PER_MILE
    }

    /** Seconds per km / mi, or null when too slow to be meaningful (< ~0.5 km/h). */
    fun paceSeconds(mps: Double, system: UnitSystem): Double? {
        if (mps < 0.14) return null
        return splitLengthM(system) / mps
    }

    fun paceUnit(system: UnitSystem): String = when (system) {
        UnitSystem.Metric -> "/km"
        UnitSystem.Imperial -> "/mi"
    }

    fun pressure(hpa: Double, system: UnitSystem): Measure = when (system) {
        UnitSystem.Metric -> Measure(hpa, "hPa")
        UnitSystem.Imperial -> Measure(hpa * INHG_PER_HPA, "inHg")
    }

    fun weight(kg: Double, system: UnitSystem): Measure = when (system) {
        UnitSystem.Metric -> Measure(kg, "kg")
        UnitSystem.Imperial -> Measure(kg * LB_PER_KG, "lb")
    }

    fun weightToKg(value: Double, system: UnitSystem): Double = when (system) {
        UnitSystem.Metric -> value
        UnitSystem.Imperial -> value / LB_PER_KG
    }

    fun height(cm: Double, system: UnitSystem): String = when (system) {
        UnitSystem.Metric -> "${cm.roundToInt()} cm"
        UnitSystem.Imperial -> {
            val inches = (cm / CM_PER_IN).roundToInt()
            "${inches / 12}′ ${inches % 12}″"
        }
    }
}

object Format {
    /** H:MM:SS, or M:SS under an hour. */
    fun duration(seconds: Double): String {
        val s = seconds.coerceAtLeast(0.0).roundToLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** 5:07 /km */
    fun pace(mps: Double, system: UnitSystem): String {
        val p = Units.paceSeconds(mps, system) ?: return "–:–– ${Units.paceUnit(system)}"
        if (p >= 3600) return "> 60:00 ${Units.paceUnit(system)}"
        val total = p.roundToLong()
        return String.format(Locale.US, "%d:%02d %s", total / 60, total % 60, Units.paceUnit(system))
    }

    fun paceValue(mps: Double, system: UnitSystem): String {
        val p = Units.paceSeconds(mps, system) ?: return "–:––"
        if (p >= 3600) return "60:00+"
        val total = p.roundToLong()
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    fun speed(mps: Double, system: UnitSystem, decimals: Int = 1) = Units.speed(mps, system).format(decimals)

    /** Distance with sensible precision: 0.42 km, 12.4 km, 128 km. */
    fun distance(m: Double, system: UnitSystem): String {
        val d = Units.distance(m, system)
        val decimals = when {
            d.value < 10 -> 2
            d.value < 100 -> 1
            else -> 0
        }
        return d.format(decimals)
    }

    fun altitude(m: Double?, system: UnitSystem): String =
        if (m == null) "–" else Units.altitude(m, system).format(0)

    fun coordinate(deg: Double, positive: Char, negative: Char): String =
        "${String.format(Locale.US, "%.5f", abs(deg))}° ${if (deg >= 0) positive else negative}"

    /** 8-point compass label for a bearing in degrees. */
    fun compass(bearing: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((bearing % 360) + 360) % 360 / 45.0).roundToInt() % 8
        return dirs[idx]
    }
}

/** Fixed decimals; a small negative that rounds to zero prints as "0", not "-0" (e.g. vertical speed at rest). */
fun Double.fmt(decimals: Int): String {
    val v = if ((this * 10.0.pow(decimals)).roundToLong() == 0L) 0.0 else this
    return String.format(Locale.getDefault(), "%.${decimals}f", v)
}
