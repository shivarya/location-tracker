package com.shivarya.locationspeedtracker.core.tracking

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** Running totals for a session, all SI. Persisted on the session row. */
@Serializable
data class SessionStats(
    val distanceM: Double = 0.0,
    val movingS: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    /** Slowest speed while moving (the Expo app's "min speed": the minimum positive speed). 0 = none yet. */
    val minMovingSpeedMps: Double = 0.0,
    val elevGainM: Double = 0.0,
    val elevLossM: Double = 0.0,
    val minAltM: Double? = null,
    val maxAltM: Double? = null,
    val acceptedPoints: Int = 0,
    val rejectedPoints: Int = 0,
) {
    val avgMovingSpeedMps: Double get() = if (movingS > 0) distanceM / movingS else 0.0
    val qualityScore: Int
        get() {
            val total = acceptedPoints + rejectedPoints
            return if (total == 0) 100 else (acceptedPoints * 100.0 / total).toInt()
        }
}

/**
 * Elevation gain/loss with hysteresis on a lightly smoothed series: a climb only starts counting once it rises
 * [threshold] above the last turning point, then follows the trend; small dips inside a climb are ignored.
 * GNSS altitude wants ~4 m, barometric ~1 m.
 */
class ElevationAccumulator(
    var threshold: Double = 4.0,
    private val maxVerticalAccuracy: Double = 25.0,
    private val alpha: Double = 0.3,
) {
    var gain = 0.0; private set
    var loss = 0.0; private set
    var min: Double? = null; private set
    var max: Double? = null; private set
    private var smoothed: Double? = null
    private var ref: Double? = null
    private var trend = 0

    fun add(altitude: Double?, verticalAccuracy: Double? = null) {
        if (altitude == null || !altitude.isFinite()) return
        if (verticalAccuracy != null && verticalAccuracy > maxVerticalAccuracy) return
        val s = smoothed?.let { it + alpha * (altitude - it) } ?: altitude
        smoothed = s
        min = min?.let { min(it, s) } ?: s
        max = max?.let { max(it, s) } ?: s
        val r = ref
        if (r == null) {
            ref = s
            return
        }
        when {
            trend > 0 && s > r -> { gain += s - r; ref = s }
            trend < 0 && s < r -> { loss += r - s; ref = s }
            trend >= 0 && r - s >= threshold -> { loss += r - s; ref = s; trend = -1 }
            trend <= 0 && s - r >= threshold -> { gain += s - r; ref = s; trend = 1 }
        }
    }

    /** After a pause the next altitude starts fresh (no climb counted across the gap). */
    fun breakSegment() {
        smoothed = null
        ref = null
        trend = 0
    }

    fun restore(gain: Double, loss: Double, min: Double?, max: Double?) {
        this.gain = gain
        this.loss = loss
        this.min = min
        this.max = max
    }
}

class StatsAccumulator(
    private val movingThresholdMps: Double,
    elevationThresholdM: Double = 4.0,
) {
    var stats = SessionStats(); private set
    private var lastSpeed: Double? = null
    private val elevation = ElevationAccumulator(elevationThresholdM)
    private var barometric = false

    fun restore(s: SessionStats) {
        stats = s
        elevation.restore(s.elevGainM, s.elevLossM, s.minAltM, s.maxAltM)
    }

    fun onRejected() {
        stats = stats.copy(rejectedPoints = stats.rejectedPoints + 1)
    }

    fun onAccepted(fix: Fix, v: Verdict.Accepted, externalAltitude: Double? = null) {
        val movingAdd = when {
            v.dtS <= 0 || !v.moving -> 0.0
            v.dtS <= MAX_GAP_S -> v.dtS
            // Signal gap: only count it as moving if we actually covered ground at a moving pace.
            v.distanceAddedM / v.dtS >= movingThresholdMps -> v.dtS
            else -> 0.0
        }
        // A max-speed spike must be confirmed by a neighbouring sample unless the chipset vouches for it.
        val candidate = if ((fix.speedAcc ?: Double.MAX_VALUE) <= 1.0) v.speedMps else min(v.speedMps, lastSpeed ?: 0.0)
        lastSpeed = v.speedMps
        if (externalAltitude != null) {
            // Switching to the (calibrated) barometer: restart the hysteresis so the source change isn't a "climb",
            // and use a finer threshold — barometric altitude is far less noisy than GNSS.
            if (!barometric) {
                barometric = true
                elevation.threshold = BARO_THRESHOLD_M
                elevation.breakSegment()
            }
            elevation.add(externalAltitude)
        } else if (!barometric) {
            elevation.add(fix.bestAltitude, fix.vAcc)
        }
        val minMoving = if (v.moving && v.speedMps > 0) {
            if (stats.minMovingSpeedMps == 0.0) v.speedMps else min(stats.minMovingSpeedMps, v.speedMps)
        } else stats.minMovingSpeedMps
        stats = stats.copy(
            distanceM = stats.distanceM + v.distanceAddedM,
            movingS = stats.movingS + movingAdd,
            maxSpeedMps = max(stats.maxSpeedMps, candidate),
            minMovingSpeedMps = minMoving,
            elevGainM = elevation.gain,
            elevLossM = elevation.loss,
            minAltM = elevation.min,
            maxAltM = elevation.max,
            acceptedPoints = stats.acceptedPoints + 1,
        )
    }

    fun breakSegment() {
        lastSpeed = null
        elevation.breakSegment()
    }

    private companion object {
        const val MAX_GAP_S = 30.0
        const val BARO_THRESHOLD_M = 2.0
    }
}

enum class SplitKind(val stored: String) {
    Auto("auto"), Manual("manual");

    companion object {
        fun from(s: String) = entries.firstOrNull { it.stored == s } ?: Auto
    }
}

data class Split(
    val kind: SplitKind,
    val index: Int,
    val startTime: Long,
    val endTime: Long,
    val distanceM: Double,
    val movingS: Double,
    val maxSpeedMps: Double,
    val elevGainM: Double,
)

/**
 * Cuts a session into laps. Auto splits fall every [splitLengthM] of cumulative distance, with the crossing time
 * interpolated between fixes; manual laps end whenever [manualLap] is called.
 */
class SplitTracker(private val kind: SplitKind, var splitLengthM: Double) {
    private var index = 0
    private var startTime = 0L
    private var startDistance = 0.0
    private var startMoving = 0.0
    private var startGain = 0.0
    private var maxSpeed = 0.0
    private var lastTime = 0L
    private var lastDistance = 0.0

    fun start(time: Long, stats: SessionStats, nextIndex: Int = 0) {
        index = nextIndex
        startTime = time
        startDistance = stats.distanceM
        startMoving = stats.movingS
        startGain = stats.elevGainM
        maxSpeed = 0.0
        lastTime = time
        lastDistance = stats.distanceM
    }

    /** Feed every accepted fix; returns the auto splits completed by it (usually none, rarely more than one). */
    fun onUpdate(time: Long, stats: SessionStats, speedMps: Double): List<Split> {
        maxSpeed = max(maxSpeed, speedMps)
        val out = mutableListOf<Split>()
        if (kind == SplitKind.Auto && splitLengthM > 0) {
            while (stats.distanceM - startDistance >= splitLengthM) {
                val boundary = startDistance + splitLengthM
                val span = stats.distanceM - lastDistance
                val frac = if (span > 0) ((boundary - lastDistance) / span).coerceIn(0.0, 1.0) else 1.0
                val t = lastTime + ((time - lastTime) * frac).toLong()
                val movingAtBoundary = startMoving + (stats.movingS - startMoving) * fractionOf(boundary, stats)
                out += Split(kind, index++, startTime, t, splitLengthM, movingAtBoundary - startMoving, maxSpeed, stats.elevGainM - startGain)
                startTime = t
                startDistance = boundary
                startMoving = movingAtBoundary
                startGain = stats.elevGainM
                maxSpeed = speedMps
            }
        }
        lastTime = time
        lastDistance = stats.distanceM
        return out
    }

    private fun fractionOf(boundary: Double, stats: SessionStats): Double {
        val covered = stats.distanceM - startDistance
        return if (covered > 0) ((boundary - startDistance) / covered).coerceIn(0.0, 1.0) else 1.0
    }

    /** Close the current lap now (manual lap button, or the tail end of a session). Null if nothing happened. */
    fun manualLap(time: Long, stats: SessionStats): Split? {
        val d = stats.distanceM - startDistance
        if (d <= 0.5 && time - startTime < 1000) return null
        val s = Split(kind, index++, startTime, time, d, stats.movingS - startMoving, maxSpeed, stats.elevGainM - startGain)
        startTime = time
        startDistance = stats.distanceM
        startMoving = stats.movingS
        startGain = stats.elevGainM
        maxSpeed = 0.0
        return s
    }
}
