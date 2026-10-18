package com.shivarya.locationspeedtracker.core.sensors

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

const val STANDARD_GRAVITY = 9.80665f
const val STANDARD_PRESSURE_HPA = 1013.25f

/** Pure maths behind the G-meter, compass and barometer — no Android types, unit-tested on the JVM. */
object MotionMath {
    /** Device-frame vector → world frame (East, North, Up) with the row-major rotation matrix from the rotation vector. */
    fun toWorld(r: FloatArray, v: FloatArray): FloatArray = floatArrayOf(
        r[0] * v[0] + r[1] * v[1] + r[2] * v[2],
        r[3] * v[0] + r[4] * v[1] + r[5] * v[2],
        r[6] * v[0] + r[7] * v[1] + r[8] * v[2],
    )

    /**
     * Horizontal world acceleration split along the direction of travel: (longitudinal, lateral) in g.
     * Longitudinal > 0 = accelerating, < 0 = braking; lateral > 0 = pushed to the right (turning left).
     */
    fun vehicleG(east: Float, north: Float, headingDeg: Double): Pair<Float, Float> {
        val h = Math.toRadians(headingDeg)
        val fwdE = sin(h)
        val fwdN = cos(h)
        val long = east * fwdE + north * fwdN
        val lat = east * fwdN - north * fwdE
        return (long / STANDARD_GRAVITY).toFloat() to (lat / STANDARD_GRAVITY).toFloat()
    }

    /**
     * Compass heading (0–360°, magnetic) of the direction the phone "faces": the top edge when lying flat, the
     * camera when held upright (dashboard / handlebar mount). [r] is the row-major device→world rotation matrix.
     */
    fun facingAzimuth(r: FloatArray): Double {
        // Device Z axis in world coordinates (third column). Upright phone: Z is roughly horizontal.
        val zE = r[2]
        val zN = r[5]
        val zU = r[8]
        val upright = abs(zU) < 0.7f
        val (e, n) = if (upright) -zE to -zN else r[1] to r[4] // camera looks along −Z; top edge is +Y
        return (Math.toDegrees(atan2(e.toDouble(), n.toDouble())) + 360) % 360
    }

    /** Pitch and roll (degrees) of the phone relative to gravity, from the device-frame gravity vector. */
    fun tilt(gravity: FloatArray): Pair<Float, Float> {
        val (x, y, z) = Triple(gravity[0], gravity[1], gravity[2])
        val pitch = Math.toDegrees(atan2(-y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
        val roll = Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()
        return pitch to roll
    }

    /**
     * Lean (bank) angle for a phone mounted upright on handlebars or a dash: rotation about the forward axis,
     * from gravity in the device plane. 0 = upright, + = leaning right. Null when the phone is closer to flat than to
     * upright (under half of gravity in the screen plane) — the angle would be sensor noise.
     */
    fun lean(gravity: FloatArray): Float? {
        val (x, y, z) = Triple(gravity[0], gravity[1], gravity[2])
        if (hypot(x, y) < 0.5f * sqrt(x * x + y * y + z * z)) return null
        return Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
    }

    /** [a] − [b] in degrees, folded into −180..180. */
    fun angleDelta(a: Float, b: Float): Float = (a - b + 540f) % 360f - 180f

    /** International barometric formula (same as SensorManager.getAltitude). */
    fun pressureAltitude(hpa: Float, seaLevelHpa: Float = STANDARD_PRESSURE_HPA): Float =
        (44330.0 * (1.0 - Math.pow((hpa / seaLevelHpa).toDouble(), 1.0 / 5.255))).toFloat()

    fun horizontal(east: Float, north: Float) = hypot(east, north)
}

/**
 * Barometric altitude, offset-calibrated to GNSS mean-sea-level altitude whenever a fix has a tight vertical
 * accuracy. The barometer resolves ~0.1 m changes (great for climb/descent) but drifts with the weather; GNSS is
 * absolute but noisy — together they give a smooth, correct altitude.
 */
class BaroAltimeter(private val calibrationAlpha: Float = 0.05f, private val smoothAlpha: Float = 0.2f) {
    private var offset: Float? = null
    private var calibrations = 0
    var smoothed: Float? = null; private set
    var verticalSpeed: Float = 0f; private set
    private var lastT = 0L
    private var lastAlt: Float? = null

    val calibrated get() = calibrations >= 3

    /** Feed a pressure reading at [timeMs]. */
    fun onPressure(hpa: Float, timeMs: Long) {
        val raw = MotionMath.pressureAltitude(hpa)
        val s = smoothed?.let { it + smoothAlpha * (raw - it) } ?: raw
        smoothed = s
        val prev = lastAlt
        if (prev != null && timeMs - lastT >= 1000) {
            val dt = (timeMs - lastT) / 1000f
            verticalSpeed = verticalSpeed * 0.5f + ((s - prev) / dt) * 0.5f
            lastAlt = s
            lastT = timeMs
        } else if (prev == null) {
            lastAlt = s
            lastT = timeMs
        }
    }

    /** Pull the offset toward a trustworthy GNSS altitude. */
    fun onGnssAltitude(mslM: Double, verticalAccuracyM: Double?) {
        val s = smoothed ?: return
        if (verticalAccuracyM == null || verticalAccuracyM > 8.0) return
        val target = (mslM - s).toFloat()
        offset = offset?.let { it + calibrationAlpha * (target - it) } ?: target
        calibrations++
    }

    /** Calibrated altitude (m, MSL) when calibrated, else the standard-atmosphere pressure altitude. */
    val altitude: Float? get() = smoothed?.let { it + (offset ?: 0f) }
}

/** Steps per minute from step-detector timestamps over a sliding window. */
class CadenceMeter(private val windowMs: Long = 10_000) {
    private val times = ArrayDeque<Long>()

    fun onStep(timeMs: Long) {
        times.addLast(timeMs)
        trim(timeMs)
    }

    fun cadence(nowMs: Long): Float {
        trim(nowMs)
        if (times.size < 3) return 0f
        val span = (times.last() - times.first()).coerceAtLeast(1)
        return (times.size - 1) * 60_000f / span
    }

    private fun trim(now: Long) {
        while (times.isNotEmpty() && now - times.first() > windowMs) times.removeFirst()
    }
}
