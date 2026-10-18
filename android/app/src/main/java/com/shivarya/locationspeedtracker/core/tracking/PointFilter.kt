package com.shivarya.locationspeedtracker.core.tracking

import com.shivarya.locationspeedtracker.core.geo.Geo
import com.shivarya.locationspeedtracker.core.model.ActivityType
import kotlin.math.abs
import kotlin.math.max

/** One position fix, decoupled from android.location.Location so the maths is JVM-testable. */
data class Fix(
    /** UTC epoch millis. */
    val time: Long,
    val lat: Double,
    val lon: Double,
    /** WGS84 ellipsoid height (what GNSS reports). */
    val altitude: Double? = null,
    /** Mean-sea-level altitude (AltitudeConverter / Location.getMslAltitudeMeters), when known. */
    val altitudeMsl: Double? = null,
    /** Doppler speed in m/s. */
    val speed: Double? = null,
    val bearing: Double? = null,
    val hAcc: Double? = null,
    val vAcc: Double? = null,
    val speedAcc: Double? = null,
    val bearingAcc: Double? = null,
    val provider: String? = null,
    val satellites: Int? = null,
    val isMock: Boolean = false,
    /** SystemClock.elapsedRealtimeNanos of the fix — monotonic, preferred for intervals. */
    val elapsedNanos: Long? = null,
) {
    /** Best altitude for elevation maths: MSL when we have it, else the raw ellipsoid height. */
    val bestAltitude: Double? get() = altitudeMsl ?: altitude
}

/**
 * Thresholds, ported from the 1.0.7 Expo build's `trackingQuality.ts` (`master` branch) with two fixes:
 * the speed ceiling depends on the activity (the old fixed 55 m/s rejected anything over 198 km/h), and jitter is
 * measured from the last point that added distance — the old point-to-point test threw away every 1 s step of a
 * walk (1.4 m < the 5 m noise floor), so walks recorded ~0 km.
 */
data class FilterConfig(
    val maxAccuracyM: Double = 40.0,
    val minIntervalMs: Long = 800,
    val maxSpeedMps: Double = 110.0,
    val movingThresholdMps: Double = 0.5,
    val maxAccelMps2: Double = 12.0,
    val accelSpeedFloorMps: Double = 8.0,
    val noiseFloorM: Double = 5.0,
    val accuracyNoiseMultiplier: Double = 0.8,
    val speedAgreementMps: Double = 4.0,
    val missingAccuracyM: Double = 99.0,
    /** After this many consecutive rejections (accuracy aside), accept and re-anchor so we can't lock out forever. */
    val maxRejectStreak: Int = 5,
) {
    companion object {
        fun forActivity(activity: ActivityType, maxAccuracyM: Double) = FilterConfig(
            maxAccuracyM = maxAccuracyM,
            maxSpeedMps = activity.maxValidSpeedMps,
            movingThresholdMps = activity.movingThresholdMps,
        )
    }
}

enum class RejectReason { Accuracy, TooSoon, OutOfOrder, SpeedSpike, Acceleration, PositionJump }

sealed interface Verdict {
    data class Rejected(val reason: RejectReason) : Verdict

    data class Accepted(
        /** Distance this fix adds to the session (0 while stationary / inside the noise radius). */
        val distanceAddedM: Double,
        /** Best speed estimate for this fix (Doppler when trustworthy, else position-derived). */
        val speedMps: Double,
        val moving: Boolean,
        /** Seconds since the previous accepted fix (0 for the first fix of a segment). */
        val dtS: Double,
        val firstOfSegment: Boolean,
    ) : Verdict
}

class PointFilter(var config: FilterConfig) {
    private var prev: Fix? = null
    private var anchor: Fix? = null
    private var prevSpeed = 0.0
    private var rejectStreak = 0

    /** Start a new segment (session start, resume after pause): the next fix adds no distance. */
    fun reset() {
        prev = null
        anchor = null
        prevSpeed = 0.0
        rejectStreak = 0
    }

    private fun acc(f: Fix) = f.hAcc?.takeIf { it > 0 } ?: config.missingAccuracyM

    private fun intervalS(a: Fix, b: Fix): Double {
        val an = a.elapsedNanos
        val bn = b.elapsedNanos
        return if (an != null && bn != null) (bn - an) / 1e9 else (b.time - a.time) / 1000.0
    }

    fun offer(fix: Fix): Verdict {
        if (acc(fix) > config.maxAccuracyM) return Verdict.Rejected(RejectReason.Accuracy)

        val p = prev
        if (p == null) {
            prev = fix
            anchor = fix
            prevSpeed = validGpsSpeed(fix) ?: 0.0
            rejectStreak = 0
            return Verdict.Accepted(0.0, prevSpeed, prevSpeed >= config.movingThresholdMps, 0.0, firstOfSegment = true)
        }

        val dt = intervalS(p, fix)
        if (dt <= 0) return reject(RejectReason.OutOfOrder, fix)
        if (dt * 1000 < config.minIntervalMs) return Verdict.Rejected(RejectReason.TooSoon)

        val dPrev = Geo.distance(p.lat, p.lon, fix.lat, fix.lon)
        val derived = dPrev / dt
        val gps = validGpsSpeed(fix)

        if (derived > config.maxSpeedMps) return reject(RejectReason.SpeedSpike, fix)
        // Doppler says we're barely moving but the position leapt: multipath / Wi-Fi fix jump.
        if (gps != null && derived - gps > max(config.speedAgreementMps * 2, 8.0) + acc(fix) / dt) {
            return reject(RejectReason.PositionJump, fix)
        }

        val speed = when {
            gps == null -> derived
            abs(gps - derived) <= config.speedAgreementMps -> gps
            (fix.speedAcc ?: Double.MAX_VALUE) <= 1.0 -> gps
            // Doppler says we're stopped: position differences are just noise.
            gps < config.movingThresholdMps -> gps
            else -> derived
        }
        if (prevSpeed > 0 && speed > config.accelSpeedFloorMps && abs(speed - prevSpeed) / dt > config.maxAccelMps2) {
            return reject(RejectReason.Acceleration, fix)
        }

        val a = anchor ?: p
        val dAnchor = Geo.distance(a.lat, a.lon, fix.lat, fix.lon)
        val noise = max(config.noiseFloorM, max(acc(a), acc(fix)) * config.accuracyNoiseMultiplier)
        val moved = dAnchor > noise && (speed >= config.movingThresholdMps * 0.5 || dAnchor > 2 * noise)
        val added = if (moved) dAnchor else 0.0
        if (moved) anchor = fix

        prev = fix
        prevSpeed = speed
        rejectStreak = 0
        return Verdict.Accepted(added, speed, speed >= config.movingThresholdMps, dt, firstOfSegment = false)
    }

    private fun validGpsSpeed(f: Fix): Double? = f.speed?.takeIf { it >= 0 && it <= config.maxSpeedMps }

    /** Count the rejection; past the streak limit, re-anchor on this fix (adds no distance) instead. */
    private fun reject(reason: RejectReason, fix: Fix): Verdict {
        rejectStreak++
        if (rejectStreak < config.maxRejectStreak) return Verdict.Rejected(reason)
        prev = fix
        anchor = fix
        prevSpeed = validGpsSpeed(fix) ?: 0.0
        rejectStreak = 0
        return Verdict.Accepted(0.0, prevSpeed, prevSpeed >= config.movingThresholdMps, 0.0, firstOfSegment = true)
    }
}
