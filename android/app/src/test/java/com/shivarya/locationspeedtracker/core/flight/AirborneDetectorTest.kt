package com.shivarya.locationspeedtracker.core.flight

import com.shivarya.locationspeedtracker.core.tracking.Fix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirborneDetectorTest {
    private fun fixes(seconds: Int, speed: Double, start: Long = 0L) =
        (0..seconds).map { Fix(time = start + it * 1000L, lat = 0.0, lon = 0.0, speed = speed, speedAcc = 1.0) }

    @Test
    fun `a sustained climb-out speed is a flight`() {
        val d = AirborneDetector()
        val verdicts = fixes(30, 140.0).map(d::offer)
        assertFalse(verdicts.take(20).any { it })
        assertTrue(verdicts.last())
    }

    @Test
    fun `a high-speed train or a fast drive is not`() {
        val d = AirborneDetector()
        assertFalse(fixes(600, 89.0).map(d::offer).any { it }) // 320 km/h
    }

    @Test
    fun `a slow fix restarts the count`() {
        val d = AirborneDetector()
        val a = fixes(15, 140.0)
        val slow = Fix(time = 16_000, lat = 0.0, lon = 0.0, speed = 60.0, speedAcc = 1.0)
        val b = fixes(15, 140.0, start = 17_000)
        assertFalse((a + slow + b).map(d::offer).any { it })
    }
}
