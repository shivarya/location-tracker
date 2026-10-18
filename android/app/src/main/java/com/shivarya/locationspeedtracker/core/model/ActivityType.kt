package com.shivarya.locationspeedtracker.core.model

/**
 * What a session records. Drives filtering limits, auto-pause, which number is primary (speed or pace) and which
 * sensors run. [stored] is the value persisted in Room/DataStore — never rename it.
 */
enum class ActivityType(
    val stored: String,
    val label: String,
    /** Fastest plausible speed; anything above is a GNSS glitch. */
    val maxValidSpeedMps: Double,
    /** Below this the user is "stopped" (moving time, auto-pause). */
    val movingThresholdMps: Double,
    /** Foot activities read pace (min/km) rather than speed. */
    val onFoot: Boolean,
    /** Engine-powered: G-force/lean matter, Health Connect has no exercise type for them. */
    val motorized: Boolean,
    /** Speed gauge full-scale in km/h (mph scales use the same physical speed, rounded). */
    val gaugeMaxKmh: Int,
) {
    Walk("walk", "Walk", 12.0, 0.4, onFoot = true, motorized = false, gaugeMaxKmh = 12),
    Run("run", "Run", 15.0, 0.8, onFoot = true, motorized = false, gaugeMaxKmh = 30),
    Hike("hike", "Hike", 12.0, 0.3, onFoot = true, motorized = false, gaugeMaxKmh = 12),
    Cycle("cycle", "Cycle", 30.0, 1.0, onFoot = false, motorized = false, gaugeMaxKmh = 60),
    Drive("drive", "Drive", 110.0, 1.5, onFoot = false, motorized = true, gaugeMaxKmh = 220),
    Motorbike("motorbike", "Motorbike", 110.0, 1.5, onFoot = false, motorized = true, gaugeMaxKmh = 220),
    Other("other", "General", 110.0, 0.5, onFoot = false, motorized = false, gaugeMaxKmh = 160);

    companion object {
        fun from(stored: String?): ActivityType = entries.firstOrNull { it.stored == stored } ?: Other
    }
}
