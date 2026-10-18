package com.shivarya.locationspeedtracker.core.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsAndSplitsTest {
    private fun accepted(speed: Double, dt: Double = 1.0, added: Double = speed * dt, moving: Boolean = speed > 0.5) =
        Verdict.Accepted(added, speed, moving, dt, firstOfSegment = false)

    @Test
    fun `an unconfirmed single-sample speed spike does not become the max`() {
        val s = StatsAccumulator(0.5)
        listOf(10.0, 10.0, 30.0, 10.0, 10.0).forEach { s.onAccepted(Fix(0, 0.0, 0.0), accepted(it)) }
        assertEquals(10.0, s.stats.maxSpeedMps, 0.01)
    }

    @Test
    fun `a spike the chipset vouches for (low speed accuracy) counts`() {
        val s = StatsAccumulator(0.5)
        s.onAccepted(Fix(0, 0.0, 0.0), accepted(10.0))
        s.onAccepted(Fix(0, 0.0, 0.0, speedAcc = 0.4), accepted(30.0))
        assertEquals(30.0, s.stats.maxSpeedMps, 0.01)
    }

    @Test
    fun `moving time ignores stops`() {
        val s = StatsAccumulator(0.5)
        repeat(60) { s.onAccepted(Fix(0, 0.0, 0.0), accepted(3.0)) }
        repeat(60) { s.onAccepted(Fix(0, 0.0, 0.0), accepted(0.0)) }
        assertEquals(60.0, s.stats.movingS, 0.01)
        assertEquals(3.0, s.stats.avgMovingSpeedMps, 0.01)
    }

    @Test
    fun `elevation hysteresis counts the climb and the descent, not the noise`() {
        val e = ElevationAccumulator(threshold = 4.0, alpha = 1.0)
        val profile = (0..100).map { it.toDouble() } + (99 downTo 50).map { it.toDouble() }
        // ±1.5 m wobble on top
        profile.forEachIndexed { i, a -> e.add(a + if (i % 2 == 0) 1.5 else -1.5) }
        assertEquals(100.0, e.gain, 5.0)
        assertEquals(50.0, e.loss, 5.0)
    }

    @Test
    fun `flat noisy altitude adds no gain`() {
        val e = ElevationAccumulator(threshold = 4.0)
        repeat(500) { e.add(100.0 + if (it % 3 == 0) 3.0 else -2.0) }
        assertTrue("gain ${e.gain}", e.gain < 4.0)
    }

    @Test
    fun `auto splits fall every kilometre with interpolated times`() {
        val t = SplitTracker(SplitKind.Auto, 1000.0)
        t.start(0L, SessionStats())
        val splits = mutableListOf<Split>()
        // 10 m/s, one update per 10 s (100 m)
        for (i in 1..25) {
            val stats = SessionStats(distanceM = i * 100.0, movingS = i * 10.0)
            splits += t.onUpdate(i * 10_000L, stats, 10.0)
        }
        assertEquals(2, splits.size)
        assertEquals(100_000L, splits[0].endTime)
        assertEquals(200_000L, splits[1].endTime)
        assertEquals(100.0, splits[1].movingS, 0.5)
        assertEquals(listOf(0, 1), splits.map { it.index })
    }

    @Test
    fun `manual laps close the current lap`() {
        val t = SplitTracker(SplitKind.Manual, 0.0)
        t.start(0L, SessionStats())
        t.onUpdate(5_000, SessionStats(distanceM = 50.0, movingS = 5.0), 10.0)
        val lap = t.manualLap(6_000, SessionStats(distanceM = 60.0, movingS = 6.0))!!
        assertEquals(60.0, lap.distanceM, 0.01)
        assertEquals(0, lap.index)
        val second = t.manualLap(9_000, SessionStats(distanceM = 90.0, movingS = 9.0))!!
        assertEquals(30.0, second.distanceM, 0.01)
        assertEquals(1, second.index)
    }

    @Test
    fun `chunked track shares full chunks and joins drawables`() {
        var track = ChunkedTrack.Empty
        repeat(450) { track = track.append(TrackSample(it.toLong(), it.toDouble(), 0.0, 1.0, null, 0, it.toDouble())) }
        assertEquals(450, track.size)
        assertEquals(2, track.chunks.size)
        assertEquals(50, track.tail.size)
        val drawn = track.drawableChunks()
        assertEquals(3, drawn.size)
        // Each later chunk starts with its predecessor's last point so the line is continuous.
        assertEquals(drawn[0].last(), drawn[1].first())
        assertEquals(449L, track.last!!.time)
    }
}
