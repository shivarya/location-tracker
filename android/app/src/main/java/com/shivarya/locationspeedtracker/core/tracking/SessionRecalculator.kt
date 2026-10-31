package com.shivarya.locationspeedtracker.core.tracking

import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.flight.AirborneDetector
import com.shivarya.locationspeedtracker.core.geo.Geo
import com.shivarya.locationspeedtracker.core.model.ActivityType

/** A saved session's figures worked out again from its points; [autoLaps] replaces the session's auto splits. */
data class Recalculation(
    val activity: ActivityType,
    val stats: SessionStats,
    val points: List<TrackPointEntity>,
    val autoLaps: List<LapEntity>,
    val calories: Double?,
)

/**
 * Replays a saved session's points through the current filter and stats, so a session recorded by an older,
 * stricter filter gets its distance, speeds and moving time back. Up to 2.0.5 a flight recorded as General kept only
 * every 5th cruise fix, re-anchored at speed 0: those get a speed from the neighbouring positions again. Elevation
 * stays as recorded (it may have come from the barometer, which the points don't hold), and like the engine, a session
 * that cruised at airliner speed becomes a Flight.
 */
object SessionRecalculator {
    /** A stored speed of ~0 between positions this far apart per second was a re-anchored fix, not a stop. */
    private const val REANCHOR_MIN_MPS = 20.0
    private const val REANCHOR_MAX_GAP_S = 30.0

    fun run(session: SessionEntity, points: List<TrackPointEntity>, laps: List<LapEntity>): Recalculation? {
        if (points.isEmpty()) return null
        val fixes = points.mapIndexed { i, p ->
            val prev = points.getOrNull(i - 1)?.takeIf { it.segment == p.segment }
            Fix(
                time = p.time, lat = p.lat, lon = p.lon, altitude = p.altitude, altitudeMsl = p.altitudeMsl,
                speed = restoredSpeed(prev, p), bearing = p.bearing, hAcc = p.hAcc, vAcc = p.vAcc, speedAcc = p.speedAcc,
            )
        }
        val recorded = ActivityType.from(session.activity)
        val activity = if (recorded != ActivityType.Flight && AirborneDetector().let { d -> fixes.any(d::offer) }) {
            ActivityType.Flight
        } else recorded

        // Every stored point already passed an accuracy check when it was recorded.
        val filter = PointFilter(FilterConfig.forActivity(activity, maxAccuracyM = 1000.0))
        val acc = StatsAccumulator(activity.movingThresholdMps)
        val splitLength = laps.filter { it.kind == SplitKind.Auto.stored }.maxOfOrNull { it.distanceM }
            ?.takeIf { activity != ActivityType.Flight && it > 0 }
        val splits = splitLength?.let { SplitTracker(SplitKind.Auto, it).apply { start(points.first().time, SessionStats()) } }
        val autoLaps = ArrayList<LapEntity>()
        var segment = points.first().segment
        val rebuilt = points.mapIndexed { i, p ->
            if (p.segment != segment) {
                segment = p.segment
                filter.reset()
                acc.breakSegment()
            }
            val fix = fixes[i]
            when (val v = filter.offer(fix)) {
                is Verdict.Rejected -> {
                    acc.onRejected()
                    p.copy(distanceM = acc.stats.distanceM)
                }
                is Verdict.Accepted -> {
                    acc.onAccepted(fix, v)
                    splits?.onUpdate(p.time, acc.stats, v.speedMps)?.forEach { autoLaps += it.toEntity(session.id) }
                    p.copy(speed = v.speedMps, distanceM = acc.stats.distanceM)
                }
            }
        }
        splits?.manualLap(points.last().time, acc.stats)?.takeIf { it.distanceM >= 10 }?.let { autoLaps += it.toEntity(session.id) }

        val s = acc.stats
        val stats = s.copy(
            elevGainM = session.stats.elevGainM,
            elevLossM = session.stats.elevLossM,
            minAltM = session.stats.minAltM,
            maxAltM = session.stats.maxAltM,
        )
        return Recalculation(activity, stats, rebuilt, autoLaps, session.calories.takeIf { activity.humanPowered })
    }

    private fun restoredSpeed(prev: TrackPointEntity?, p: TrackPointEntity): Double? {
        val stored = p.speed
        if (prev == null || (stored != null && stored >= 0.5)) return stored
        val dt = (p.time - prev.time) / 1000.0
        if (dt <= 0 || dt > REANCHOR_MAX_GAP_S) return stored
        val derived = Geo.distance(prev.lat, prev.lon, p.lat, p.lon) / dt
        return if (derived >= REANCHOR_MIN_MPS) derived else stored
    }
}
