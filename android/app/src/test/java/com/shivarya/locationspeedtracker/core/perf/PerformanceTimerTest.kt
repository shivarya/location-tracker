package com.shivarya.locationspeedtracker.core.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTimerTest {
    /** Stationary for 3 s, then constant [accel] m/s² from [launchAt] s, sampled at [hz]. */
    private fun accelRun(accel: Double, launchAt: Double, hz: Double, seconds: Double, timer: PerformanceTimer): PerfState {
        var t = 0.0
        var st: PerfState = PerfState.Arming
        while (t <= seconds) {
            val v = if (t < launchAt) 0.0 else accel * (t - launchAt)
            st = timer.onSample(PerfSample((t * 1000).toLong(), v))
            if (st is PerfState.Done) return st
            t += 1 / hz
        }
        return st
    }

    private val zeroTo100 = PerfMode.Accel(0.0, 100 / 3.6, "0–100")

    @Test
    fun `0-100 at 10 Hz is exact to a few hundredths`() {
        // 4 m/s² → 27.78 / 4 = 6.944 s, launching between fixes.
        val r = (accelRun(4.0, 3.05, 10.0, 20.0, PerformanceTimer(zeroTo100, rollout = false)) as PerfState.Done).result
        assertEquals(6.944, r.seconds, 0.03)
        assertEquals(0.41, r.peakG, 0.02)
        assertEquals(96.45, r.distanceM, 1.5) // ½ a t²
    }

    @Test
    fun `0-100 at 1 Hz is still within a tenth thanks to launch extrapolation`() {
        val r = (accelRun(4.0, 3.4, 1.0, 20.0, PerformanceTimer(zeroTo100, rollout = false)) as PerfState.Done).result
        assertEquals(6.944, r.seconds, 0.1)
    }

    @Test
    fun `rollout trims the first foot`() {
        val plain = (accelRun(4.0, 3.0, 10.0, 20.0, PerformanceTimer(zeroTo100, false)) as PerfState.Done).result
        val roll = (accelRun(4.0, 3.0, 10.0, 20.0, PerformanceTimer(zeroTo100, true)) as PerfState.Done).result
        // Covering 0.3048 m at 4 m/s² takes sqrt(2·0.3048/4) = 0.39 s.
        assertEquals(plain.seconds - 0.39, roll.seconds, 0.03)
        assertTrue(roll.rollout)
    }

    @Test
    fun `quarter mile reports time and trap speed`() {
        val mode = PerfMode.Distance(402.336, "¼ mile")
        val r = (accelRun(4.0, 2.0, 10.0, 30.0, PerformanceTimer(mode, false)) as PerfState.Done).result
        // t = sqrt(2·402.336/4) = 14.18 s, v = 56.7 m/s
        assertEquals(14.18, r.seconds, 0.05)
        assertEquals(56.7, r.endSpeedMps, 0.5)
    }

    @Test
    fun `braking 100-0 measures time and distance`() {
        val timer = PerformanceTimer(PerfMode.Braking(100 / 3.6, "100–0"), false)
        var t = 0.0
        var st: PerfState = PerfState.Arming
        // Cruise at 110 km/h for 2 s, then brake at 8 m/s² starting at t = 2.0.
        while (st !is PerfState.Done && t < 20) {
            val v = if (t < 2.0) 110 / 3.6 else (110 / 3.6 - 8 * (t - 2.0)).coerceAtLeast(0.0)
            st = timer.onSample(PerfSample((t * 1000).toLong(), v))
            t += 0.1
        }
        val r = (st as PerfState.Done).result
        // From 27.78 m/s at 8 m/s²: 3.47 s (to 0.5 m/s ≈ 3.41 s), 48.2 m.
        assertEquals(3.41, r.seconds, 0.05)
        assertEquals(48.2, r.distanceM, 1.5)
    }

    @Test
    fun `rolling 80-120 needs to start below the start speed`() {
        val mode = PerfMode.Accel(80 / 3.6, 120 / 3.6, "80–120")
        val timer = PerformanceTimer(mode, false)
        var t = 0.0
        var st: PerfState = PerfState.Arming
        while (st !is PerfState.Done && t < 30) {
            val v = 70 / 3.6 + 2.0 * t // 70 km/h + 2 m/s²
            st = timer.onSample(PerfSample((t * 1000).toLong(), v))
            t += 0.2
        }
        val r = (st as PerfState.Done).result
        // (33.33 − 22.22) / 2 = 5.56 s
        assertEquals(5.56, r.seconds, 0.05)
    }

    @Test
    fun `stored modes round-trip`() {
        PerfMode.presets(com.shivarya.locationspeedtracker.core.units.UnitSystem.Imperial).forEach {
            assertEquals(it, PerfMode.parse(it.stored, it.label))
        }
    }
}
