package com.shivarya.locationspeedtracker.core.tracking

import com.shivarya.locationspeedtracker.core.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PointFilterTest {
    private val metresPerDegLat = 111_195.0

    /** Straight line north from (12.97, 77.59) at [speed] m/s, one fix per second. */
    private fun line(seconds: Int, speed: Double, acc: Double = 5.0, start: Long = 1_700_000_000_000L): List<Fix> =
        (0..seconds).map { i ->
            Fix(time = start + i * 1000L, lat = 12.97 + i * speed / metresPerDegLat, lon = 77.59, speed = speed, hAcc = acc)
        }

    private fun run(fixes: List<Fix>, activity: ActivityType = ActivityType.Walk, maxAcc: Double = 40.0): Pair<SessionStats, List<Verdict>> {
        val f = PointFilter(FilterConfig.forActivity(activity, maxAcc))
        val s = StatsAccumulator(activity.movingThresholdMps)
        val verdicts = fixes.map { fix ->
            f.offer(fix).also { v ->
                when (v) {
                    is Verdict.Accepted -> s.onAccepted(fix, v)
                    is Verdict.Rejected -> s.onRejected()
                }
            }
        }
        return s.stats to verdicts
    }

    @Test
    fun `a 1 Hz walk keeps its distance (the Expo 1_0_7 filter recorded ~0 m)`() {
        val (stats, _) = run(line(600, 1.4))
        assertEquals(840.0, stats.distanceM, 40.0)
        assertTrue("moving time ${stats.movingS}", stats.movingS > 590)
    }

    @Test
    fun `standing still with GPS jitter adds almost no distance`() {
        val rnd = Random(42)
        val fixes = (0..300).map { i ->
            Fix(
                time = 1_700_000_000_000L + i * 1000L,
                lat = 12.97 + (rnd.nextDouble() - 0.5) * 6 / metresPerDegLat,
                lon = 77.59 + (rnd.nextDouble() - 0.5) * 6 / metresPerDegLat,
                speed = 0.1,
                hAcc = 8.0,
            )
        }
        val (stats, _) = run(fixes)
        assertTrue("distance ${stats.distanceM}", stats.distanceM < 10)
        assertEquals(0.0, stats.movingS, 5.0)
    }

    @Test
    fun `a single position spike is rejected and does not add distance`() {
        val fixes = line(60, 1.4).toMutableList()
        fixes[30] = fixes[30].copy(lat = fixes[30].lat + 500 / metresPerDegLat)
        val (stats, verdicts) = run(fixes)
        assertTrue(verdicts[30] is Verdict.Rejected)
        assertEquals(84.0, stats.distanceM, 10.0)
    }

    @Test
    fun `inaccurate fixes are rejected`() {
        val f = PointFilter(FilterConfig.forActivity(ActivityType.Run, 40.0))
        val v = f.offer(Fix(time = 0, lat = 1.0, lon = 1.0, hAcc = 80.0))
        assertEquals(Verdict.Rejected(RejectReason.Accuracy), v)
    }

    @Test
    fun `motorway speeds above the old 55 m_s ceiling are kept for drives`() {
        val (stats, verdicts) = run(line(120, 60.0, acc = 4.0), ActivityType.Drive)
        assertTrue(verdicts.drop(1).all { it is Verdict.Accepted })
        assertEquals(7200.0, stats.distanceM, 100.0)
        assertEquals(60.0, stats.maxSpeedMps, 0.5)
    }

    @Test
    fun `a genuine jump (tunnel exit) re-anchors after a streak of rejections`() {
        val a = line(10, 1.4)
        // 2 km further on, still walking: every fix looks like a spike relative to the old anchor.
        val b = line(20, 1.4, start = a.last().time + 1000).map { it.copy(lat = it.lat + 2000 / metresPerDegLat) }
        val (_, verdicts) = run(a + b)
        assertTrue(verdicts.takeLast(10).all { it is Verdict.Accepted })
    }

    @Test
    fun `fixes closer than the minimum interval are dropped`() {
        val f = PointFilter(FilterConfig.forActivity(ActivityType.Walk, 40.0))
        f.offer(Fix(time = 0, lat = 1.0, lon = 1.0, hAcc = 5.0))
        assertEquals(Verdict.Rejected(RejectReason.TooSoon), f.offer(Fix(time = 300, lat = 1.0, lon = 1.0, hAcc = 5.0)))
    }
}
