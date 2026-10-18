package com.shivarya.locationspeedtracker.core.perf

import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import kotlin.math.abs
import kotlin.math.max

/** A timed run. Speeds in m/s, distances in m. */
sealed interface PerfMode {
    val stored: String
    val label: String

    /** fromMps → toMps, accelerating. fromMps == 0 is a standing start. */
    data class Accel(val fromMps: Double, val toMps: Double, override val label: String) : PerfMode {
        override val stored get() = "accel:$fromMps:$toMps"
    }

    /** Standing start to a distance (¼ mile, 400 m…); reports time and trap speed. */
    data class Distance(val meters: Double, override val label: String) : PerfMode {
        override val stored get() = "dist:$meters"
    }

    /** fromMps → standstill; reports time and braking distance. */
    data class Braking(val fromMps: Double, override val label: String) : PerfMode {
        override val stored get() = "brake:$fromMps"
    }

    companion object {
        fun presets(units: UnitSystem): List<PerfMode> {
            fun s(v: Double) = Units.speedToMps(v, units)
            val u = Units.speedUnit(units)
            return if (units == UnitSystem.Metric) listOf(
                Accel(0.0, s(100.0), "0–100 $u"),
                Accel(0.0, s(50.0), "0–50 $u"),
                Accel(0.0, s(200.0), "0–200 $u"),
                Accel(s(80.0), s(120.0), "80–120 $u"),
                Distance(400.0, "400 m"),
                Distance(1000.0, "1 km"),
                Braking(s(100.0), "100–0 $u"),
            ) else listOf(
                Accel(0.0, s(60.0), "0–60 $u"),
                Accel(0.0, s(30.0), "0–30 $u"),
                Accel(0.0, s(100.0), "0–100 $u"),
                Accel(s(50.0), s(70.0), "50–70 $u"),
                Distance(402.336, "¼ mile"),
                Distance(804.672, "½ mile"),
                Braking(s(60.0), "60–0 $u"),
            )
        }

        fun parse(stored: String, label: String): PerfMode? {
            val p = stored.split(':')
            return runCatching {
                when (p.firstOrNull()) {
                    "accel" -> Accel(p[1].toDouble(), p[2].toDouble(), label)
                    "dist" -> Distance(p[1].toDouble(), label)
                    "brake" -> Braking(p[1].toDouble(), label)
                    else -> null
                }
            }.getOrNull()
        }
    }
}

data class PerfSample(val tMs: Long, val speedMps: Double, val speedAccuracy: Double? = null)

data class PerfResult(
    val mode: PerfMode,
    val seconds: Double,
    val distanceM: Double,
    val startSpeedMps: Double,
    val endSpeedMps: Double,
    val maxSpeedMps: Double,
    val peakG: Double,
    val rollout: Boolean,
    /** Mean fix rate during the run (Hz) — the main accuracy driver. */
    val rateHz: Double,
    /** (seconds since start, speed m/s). */
    val trace: List<Pair<Double, Double>>,
)

sealed interface PerfState {
    /** Get into position: stop (standing starts), slow below the start speed, or get above it (braking). */
    data object Arming : PerfState
    data object Ready : PerfState
    data class Running(val elapsedS: Double, val distanceM: Double) : PerfState
    data class Done(val result: PerfResult) : PerfState
}

/**
 * Drag-timer state machine over GNSS Doppler speed samples.
 *
 * - Threshold crossings are interpolated between fixes (a 1 Hz receiver would otherwise be ±0.5 s).
 * - Standing starts: the launch instant is extrapolated back from the first two moving fixes assuming constant
 *   acceleration — "first fix above 0.5 m/s" alone reads up to a whole fix interval late.
 * - Distance is integrated from speed (trapezoid), far steadier than summing position deltas over seconds.
 * - With rollout, standing-start times begin after the first foot of travel, as on a drag strip.
 */
class PerformanceTimer(val mode: PerfMode, private val rollout: Boolean) {
    var state: PerfState = PerfState.Arming; private set

    private val standing = mode is PerfMode.Distance || (mode is PerfMode.Accel && mode.fromMps < STILL_MPS)

    private var last: PerfSample? = null
    private var stillSince: Long? = null
    private var lastStill: PerfSample? = null
    private var firstMoving: PerfSample? = null

    private var t0 = 0.0 // ms (fractional: interpolated)
    private var started = false
    private var rolled = 0.0
    private var distance = 0.0
    private var maxSpeed = 0.0
    private var peakG = 0.0
    private val recent = ArrayDeque<PerfSample>()
    private var fixes = 0
    private var startSpeed = 0.0
    private val trace = ArrayList<Pair<Double, Double>>()

    fun reset() {
        state = PerfState.Arming
        stillSince = null
        lastStill = null
        firstMoving = null
    }

    fun onSample(s: PerfSample): PerfState {
        val prev = last
        last = s
        if (prev != null && s.tMs <= prev.tMs) return state
        when (state) {
            is PerfState.Done -> {}
            PerfState.Arming, PerfState.Ready -> arm(prev, s)
            is PerfState.Running -> if (prev != null) run(prev, s)
        }
        return state
    }

    private fun arm(prev: PerfSample?, s: PerfSample) {
        if (standing) {
            if (s.speedMps < STILL_MPS) {
                val since = stillSince ?: s.tMs.also { stillSince = it }
                lastStill = s
                if (s.tMs - since >= 1000) state = PerfState.Ready
            } else if (state == PerfState.Ready) {
                // Launch fix: the exact start is worked out on the next fix (needs an acceleration estimate).
                firstMoving = s
                state = PerfState.Running(0.0, 0.0)
            } else {
                stillSince = null
            }
            return
        }
        when (val m = mode) {
            is PerfMode.Accel -> when {
                s.speedMps < m.fromMps - ARM_MARGIN_MPS -> state = PerfState.Ready
                state == PerfState.Ready && prev != null && prev.speedMps < m.fromMps && s.speedMps >= m.fromMps -> {
                    begin(crossing(prev, s, m.fromMps), m.fromMps, startedNow = true)
                    advance(crossing(prev, s, m.fromMps), m.fromMps, prev, s)
                }
            }
            is PerfMode.Braking -> when {
                s.speedMps > m.fromMps + ARM_MARGIN_MPS -> state = PerfState.Ready
                state == PerfState.Ready && prev != null && prev.speedMps > m.fromMps && s.speedMps <= m.fromMps -> {
                    begin(crossing(prev, s, m.fromMps), m.fromMps, startedNow = true)
                    advance(crossing(prev, s, m.fromMps), m.fromMps, prev, s)
                }
            }
            is PerfMode.Distance -> {}
        }
    }

    private fun begin(tMs: Double, fromSpeed: Double, startedNow: Boolean) {
        t0 = tMs
        started = startedNow
        rolled = 0.0
        distance = 0.0
        maxSpeed = fromSpeed
        peakG = 0.0
        recent.clear()
        fixes = 0
        startSpeed = fromSpeed
        trace.clear()
        if (started) trace += 0.0 to fromSpeed
        state = PerfState.Running(0.0, 0.0)
    }

    private fun run(prev: PerfSample, s: PerfSample) {
        val s1 = firstMoving
        if (s1 != null) {
            // Second moving fix of a standing start: fix the launch instant, then account for travel up to s1.
            firstMoving = null
            val p0 = lastStill ?: prev
            var a = (s.speedMps - s1.speedMps) / ((s.tMs - s1.tMs) / 1000.0)
            if (a < 0.2) a = (s1.speedMps - p0.speedMps) / ((s1.tMs - p0.tMs) / 1000.0)
            val accel = max(a, 0.2)
            // The last "still" fix may already be creeping (< 0.5 m/s), so the launch can precede it.
            val earliest = p0.tMs - p0.speedMps / accel * 1000 - 50
            val launch = (s1.tMs - s1.speedMps / accel * 1000).coerceIn(earliest, s1.tMs.toDouble())
            begin(launch, 0.0, startedNow = !rollout)
            // Constant acceleration from rest up to s1: area of the triangle.
            addDistance(0.5 * s1.speedMps * (s1.tMs - launch) / 1000.0, s1)
            if (state !is PerfState.Running) return
            advance(s1.tMs.toDouble(), s1.speedMps, s1, s)
            return
        }
        advance(max(prev.tMs.toDouble(), t0), speedAt(prev, s, max(prev.tMs.toDouble(), t0)), prev, s)
    }

    /** Travel from ([fromT], [fromV]) to fix [s], then check whether the run is complete. */
    private fun advance(fromT: Double, fromV: Double, prev: PerfSample, s: PerfSample) {
        fixes++
        addDistance((fromV + s.speedMps) / 2 * (s.tMs - fromT) / 1000.0, s)
        if (state !is PerfState.Running) return
        maxSpeed = max(maxSpeed, s.speedMps)
        // G from the speed change over ≥ 0.5 s: fix-to-fix deltas over uneven or very short intervals (fix jitter,
        // repeated fixes) produce absurd spikes.
        recent.addLast(s)
        while (recent.size > 2 && s.tMs - recent[1].tMs >= 500) recent.removeFirst()
        val base = recent.first()
        val span = (s.tMs - base.tMs) / 1000.0
        if (span >= 0.5) peakG = max(peakG, abs(s.speedMps - base.speedMps) / span / 9.80665)
        val elapsed = ((s.tMs - t0) / 1000.0).coerceAtLeast(0.0)
        if (started) trace += elapsed to s.speedMps

        when (val m = mode) {
            is PerfMode.Accel -> when {
                started && s.speedMps >= m.toMps -> finish(crossing(prev, s, m.toMps), m.toMps)
                !standing && s.speedMps < m.fromMps - ARM_MARGIN_MPS -> reset()
                standing && s.speedMps < STILL_MPS && elapsed > 2 -> reset()
                else -> state = PerfState.Running(elapsed, distance)
            }
            is PerfMode.Distance -> when {
                started && distance >= m.meters -> {
                    val v = max(s.speedMps, 0.1)
                    finish(s.tMs - (distance - m.meters) / v * 1000, s.speedMps)
                }
                s.speedMps < STILL_MPS && elapsed > 2 -> reset()
                else -> state = PerfState.Running(elapsed, distance)
            }
            is PerfMode.Braking -> when {
                s.speedMps <= STILL_MPS -> finish(crossing(prev, s, STILL_MPS), 0.0)
                s.speedMps > m.fromMps + ARM_MARGIN_MPS -> reset()
                else -> state = PerfState.Running(elapsed, distance)
            }
        }
    }

    private fun addDistance(step: Double, at: PerfSample) {
        if (started) {
            distance += step
            return
        }
        rolled += step
        if (rolled >= ROLLOUT_M) {
            // Re-zero clock and distance at the point the first foot was covered.
            val over = rolled - ROLLOUT_M
            t0 = at.tMs - over / max(at.speedMps, 0.1) * 1000
            started = true
            distance = over
            startSpeed = 0.0
            trace += 0.0 to 0.0
        }
    }

    private fun finish(tEnd: Double, endSpeed: Double) {
        val seconds = ((tEnd - t0) / 1000.0).coerceAtLeast(0.0)
        trace += seconds to endSpeed
        state = PerfState.Done(
            PerfResult(
                mode = mode,
                seconds = seconds,
                distanceM = if (mode is PerfMode.Distance) mode.meters else distance,
                startSpeedMps = startSpeed,
                endSpeedMps = endSpeed,
                maxSpeedMps = max(maxSpeed, endSpeed),
                peakG = peakG,
                rollout = rollout && standing,
                rateHz = if (seconds > 0) fixes / seconds else 0.0,
                trace = trace.toList(),
            ),
        )
    }

    private fun crossing(a: PerfSample, b: PerfSample, v: Double): Double {
        val dv = b.speedMps - a.speedMps
        if (abs(dv) < 1e-9) return b.tMs.toDouble()
        return a.tMs + ((v - a.speedMps) / dv).coerceIn(0.0, 1.0) * (b.tMs - a.tMs)
    }

    private fun speedAt(a: PerfSample, b: PerfSample, t: Double): Double {
        val span = (b.tMs - a.tMs).toDouble()
        if (span <= 0) return b.speedMps
        return a.speedMps + (b.speedMps - a.speedMps) * ((t - a.tMs) / span).coerceIn(0.0, 1.0)
    }

    companion object {
        const val STILL_MPS = 0.5
        const val ARM_MARGIN_MPS = 0.8
        const val ROLLOUT_M = 0.3048
    }
}
