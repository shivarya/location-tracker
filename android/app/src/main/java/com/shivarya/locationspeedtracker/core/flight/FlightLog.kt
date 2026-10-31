package com.shivarya.locationspeedtracker.core.flight

import com.shivarya.locationspeedtracker.core.geo.Geo
import com.shivarya.locationspeedtracker.core.tracking.TrackSample

enum class FlightPhase(val label: String) {
    Gate("At the gate"),
    Taxi("Taxiing"),
    TakeoffRoll("Takeoff roll"),
    Climb("Climbing"),
    Cruise("Cruising"),
    Descent("Descending"),
    Airborne("Airborne"),
    Landed("Landed"),
}

data class FlightSummary(
    val phase: FlightPhase,
    val takeoffAt: Long?,
    /** Last touchdown; null while (still) airborne. */
    val landingAt: Long?,
    /** Time in the air, summed over every leg (a stopover counts twice). */
    val airborneMs: Long,
    val legs: Int,
    /** Ground track flown between takeoff and landing. */
    val airborneDistanceM: Double,
    /** Straight-line distance from the first to the last point. */
    val directDistanceM: Double,
    /** Highest smoothed GNSS altitude, MSL. */
    val maxAltitudeM: Double?,
    /** Median ground speed while cruising (null before any cruise). */
    val cruiseSpeedMps: Double?,
    val maxSpeedMps: Double,
    /** GNSS-derived, over the last ~30 s; null without enough altitude data. */
    val verticalSpeedMps: Double?,
    /** Time of the last fix; [airborneMs] is as of then. */
    val asOf: Long,
) {
    val airborne: Boolean get() = takeoffAt != null && landingAt == null

    /** Time in the air at [now], still counting through a GPS dropout while airborne. */
    fun airborneMsAt(now: Long): Long = airborneMs + if (airborne) (now - asOf).coerceAtLeast(0) else 0
}

/**
 * Flight phases from GNSS fixes alone, so it works in airplane mode: no barometer (the cabin is pressurised to
 * ~2 000 m whatever the aircraft's altitude) and no network. Takeoff is ground speed above [TAKEOFF_MPS] on two
 * fixes in a row; touchdown is two fixes below [LANDED_MPS]. GPS through a window drops out for minutes at a time, so
 * everything works across gaps: a fix at the gate after a long silence closes the leg (unless a fast one follows).
 */
class FlightLog {
    private var first: TrackSample? = null
    private var last: TrackSample? = null
    private var airborne = false
    private var pending: TrackSample? = null
    /**
     * A slow first fix after a long silence: the aircraft is down — unless the next fix is fast again, which makes it a
     * glitch (a fused fix stuck behind the aircraft read 74 km/h at 900 m on a real climb-out).
     */
    private var gapTouchdown: TrackSample? = null
    private var takeoffAt: Long? = null
    private var landingAt: Long? = null
    private var legStart = 0L
    private var legStartDistance = 0.0
    private var closedAirborneMs = 0L
    private var closedDistance = 0.0
    private var legs = 0
    private var maxSpeed = 0.0
    private var smoothedAlt: Double? = null
    private var maxAlt: Double? = null
    private val altWindow = ArrayDeque<Pair<Long, Double>>()
    private val cruiseSpeeds = ArrayList<Double>()
    private var phase = FlightPhase.Gate

    fun add(s: TrackSample) {
        if (first == null) first = s
        val prev = last
        last = s
        maxSpeed = maxOf(maxSpeed, s.speedMps)
        s.altitude?.takeIf { it.isFinite() }?.let { a ->
            // Light smoothing; a long gap starts over so a cruise-to-gate jump isn't averaged.
            val gap = prev != null && s.time - prev.time > GAP_MS
            val sm = smoothedAlt?.takeIf { !gap }?.let { it + 0.3 * (a - it) } ?: a
            smoothedAlt = sm
            if (gap) altWindow.clear()
            altWindow.addLast(s.time to sm)
            while (altWindow.size > 2 && s.time - altWindow.first().first > VS_WINDOW_MS) altWindow.removeFirst()
            if (airborne || s.speedMps >= TAKEOFF_MPS) maxAlt = maxAlt?.let { maxOf(it, sm) } ?: sm
        }

        if (!airborne) {
            if (s.speedMps >= TAKEOFF_MPS) {
                val p = pending
                if (p != null) {
                    airborne = true
                    pending = null
                    legs++
                    if (takeoffAt == null) takeoffAt = p.time
                    landingAt = null
                    legStart = p.time
                    legStartDistance = p.distanceM
                } else pending = s
            } else pending = null
        } else {
            if (s.speedMps < LANDED_MPS) {
                val p = pending
                // Two slow fixes that are kilometres apart are stale or glitched positions, not a roll-out.
                val plausible = p != null && s.time > p.time &&
                    Geo.distance(p.lat, p.lon, s.lat, s.lon) / ((s.time - p.time) / 1000.0) < TAKEOFF_MPS
                if (p != null && plausible) {
                    airborne = false
                    pending = null
                    gapTouchdown = null
                    landingAt = p.time
                    closedAirborneMs += p.time - legStart
                    closedDistance += p.distanceM - legStartDistance
                } else {
                    pending = s
                    if (prev != null && s.time - prev.time > GAP_MS) gapTouchdown = s
                }
            } else {
                pending = null
                gapTouchdown = null
            }
        }

        val vs = verticalSpeed()
        val justLanded = (gapTouchdown?.time ?: landingAt)?.let { s.time - it < LANDED_FOR_MS } == true
        phase = when {
            airborne && gapTouchdown == null -> when {
                vs == null -> FlightPhase.Airborne
                vs > CLIMB_MPS -> FlightPhase.Climb
                vs < -CLIMB_MPS -> FlightPhase.Descent
                else -> FlightPhase.Cruise
            }
            justLanded -> FlightPhase.Landed
            s.speedMps >= ROLL_MPS -> FlightPhase.TakeoffRoll
            s.speedMps >= TAXI_MPS -> FlightPhase.Taxi
            else -> FlightPhase.Gate
        }
        if (phase == FlightPhase.Cruise) cruiseSpeeds += s.speedMps
    }

    private fun verticalSpeed(): Double? {
        if (altWindow.size < 2) return null
        val (t0, a0) = altWindow.first()
        val (t1, a1) = altWindow.last()
        val dt = (t1 - t0) / 1000.0
        return if (dt >= MIN_VS_SPAN_S) (a1 - a0) / dt else null
    }

    fun summary(now: Long = last?.time ?: 0L): FlightSummary {
        val l = last
        val g = gapTouchdown
        val open = airborne && g == null
        val openMs = when {
            g != null -> g.time - legStart
            open -> (now - legStart).coerceAtLeast(0)
            else -> 0
        }
        val openDistance = when {
            g != null -> g.distanceM - legStartDistance
            open && l != null -> l.distanceM - legStartDistance
            else -> 0.0
        }
        val f = first
        return FlightSummary(
            phase = phase,
            takeoffAt = takeoffAt,
            landingAt = g?.time ?: if (airborne) null else landingAt,
            airborneMs = closedAirborneMs + openMs,
            legs = legs,
            airborneDistanceM = closedDistance + openDistance,
            directDistanceM = if (f != null && l != null) Geo.distance(f.lat, f.lon, l.lat, l.lon) else 0.0,
            maxAltitudeM = maxAlt,
            cruiseSpeedMps = cruiseSpeeds.takeIf { it.isNotEmpty() }?.sorted()?.let { it[it.size / 2] },
            maxSpeedMps = maxSpeed,
            verticalSpeedMps = if (open) verticalSpeed() else null,
            asOf = now,
        )
    }

    companion object {
        /** Ground speed that only a flying aircraft sustains (rotation is ~65–85 m/s for jets, ~50 for turboprops). */
        const val TAKEOFF_MPS = 55.0
        /** Below this after touchdown the roll-out is over. */
        const val LANDED_MPS = 35.0
        const val ROLL_MPS = 15.0
        const val TAXI_MPS = 2.0
        /** 500 ft/min, the usual line between level flight and a climb or descent. */
        const val CLIMB_MPS = 2.54
        private const val VS_WINDOW_MS = 30_000L
        private const val MIN_VS_SPAN_S = 15.0
        private const val GAP_MS = 120_000L
        /** Roll-out and taxi-in read "Landed" for this long; after that a new leg's taxi shows normally. */
        private const val LANDED_FOR_MS = 20 * 60_000L

        fun of(samples: List<TrackSample>): FlightSummary? {
            if (samples.isEmpty()) return null
            return FlightLog().apply { samples.forEach(::add) }.summary()
        }
    }
}
