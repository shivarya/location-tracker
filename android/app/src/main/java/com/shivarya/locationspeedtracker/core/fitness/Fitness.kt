package com.shivarya.locationspeedtracker.core.fitness

import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.Profile
import com.shivarya.locationspeedtracker.core.settings.Sex

/**
 * Energy expenditure, kcal per minute. With heart rate: Keytel et al. (2005) — the standard HR-based estimate for
 * steady exercise. Without: ACSM metabolic equations for walking/running (speed + grade), Compendium of Physical
 * Activities METs for cycling. Motorised activities return 0 (sitting in a vehicle isn't a workout).
 */
object CalorieCalculator {
    fun kcalPerMinute(activity: ActivityType, speedMps: Double, grade: Double, heartRate: Int?, profile: Profile, age: Int): Double {
        if (activity.motorized) return 0.0
        val w = profile.weightKg
        if (heartRate != null && heartRate >= 90) {
            val male = (-55.0969 + 0.6309 * heartRate + 0.1988 * w + 0.2017 * age) / 4.184
            val female = (-20.4022 + 0.4472 * heartRate - 0.1263 * w + 0.074 * age) / 4.184
            val v = when (profile.sex) {
                Sex.Male -> male
                Sex.Female -> female
                Sex.Unspecified -> (male + female) / 2
            }
            if (v > 0) return v
        }
        val g = grade.coerceIn(-0.15, 0.3).coerceAtLeast(0.0) // downhill isn't credited (ACSM)
        return when (activity) {
            ActivityType.Walk, ActivityType.Hike, ActivityType.Run, ActivityType.Other -> {
                val sMin = speedMps * 60 // m/min
                val running = activity == ActivityType.Run || speedMps > 2.2
                val vo2 = if (running) 0.2 * sMin + 0.9 * sMin * g + 3.5 else 0.1 * sMin + 1.8 * sMin * g + 3.5
                // ~5 kcal per litre of O2; hiking terrain and packs cost ~15% more than a flat path.
                vo2 * w / 1000 * 5 * (if (activity == ActivityType.Hike) 1.15 else 1.0)
            }
            ActivityType.Cycle -> met(cyclingMet(speedMps * 3.6), w)
            else -> 0.0
        }
    }

    private fun met(met: Double, weightKg: Double) = met * 3.5 * weightKg / 200 // kcal/min

    private fun cyclingMet(kmh: Double) = when {
        kmh < 5 -> 2.0
        kmh < 16 -> 4.0
        kmh < 19 -> 6.8
        kmh < 22 -> 8.0
        kmh < 25 -> 10.0
        kmh < 30 -> 12.0
        else -> 15.8
    }
}

/** Five heart-rate zones as % of max HR (the common 50/60/70/80/90 model). */
object HrZones {
    val bounds = listOf(0.5, 0.6, 0.7, 0.8, 0.9)
    val names = listOf("Recovery", "Endurance", "Tempo", "Threshold", "Maximum")

    /** 1..5, or 0 below zone 1. */
    fun zone(bpm: Int, maxHr: Int): Int {
        val f = bpm.toDouble() / maxHr
        return bounds.indexOfLast { f >= it } + 1
    }

    /** Lower bpm bound of each zone. */
    fun floors(maxHr: Int) = bounds.map { (it * maxHr).toInt() }
}
