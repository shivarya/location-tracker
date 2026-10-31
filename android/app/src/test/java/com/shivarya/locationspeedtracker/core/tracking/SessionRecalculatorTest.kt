package com.shivarya.locationspeedtracker.core.tracking

import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecalculatorTest {
    private val metresPerDegLat = 111_195.0

    private fun points(seconds: Int, step: Int, speed: Double, stored: (Int) -> Double) =
        (0..seconds step step).mapIndexed { i, t ->
            TrackPointEntity(
                sessionId = "s", seq = i, time = 1_700_000_000_000L + t * 1000L, lat = 12.97 + t * speed / metresPerDegLat,
                lon = 77.59, speed = stored(i), hAcc = 6.0, speedAcc = 0.4,
            )
        }

    private fun session(activity: ActivityType, stats: SessionStats = SessionStats()) =
        SessionEntity(id = "s", startTime = 0, activity = activity.stored, stats = stats, calories = 900.0)

    @Test
    fun `a General flight saved by the old filter gets its cruise back and becomes a Flight`() {
        // What 2.0.4 saved: every 5th cruise fix, its speed re-anchored to 0, and an auto split every km it did count.
        val saved = points(600, 5, 240.0) { 0.0 }
        val laps = listOf(LapEntity(sessionId = "s", kind = "auto", lapIndex = 0, startTime = 0, endTime = 1, distanceM = 1000.0, movingS = 1.0, maxSpeedMps = 1.0, elevGainM = 0.0))
        val elevation = SessionStats(distanceM = 1_200.0, elevGainM = 300.0, maxAltM = 12_000.0)
        val r = SessionRecalculator.run(session(ActivityType.Other, elevation), saved, laps)!!
        assertEquals(ActivityType.Flight, r.activity)
        assertEquals(144_000.0, r.stats.distanceM, 1_500.0)
        assertEquals(240.0, r.stats.maxSpeedMps, 2.0)
        assertTrue("moving ${r.stats.movingS}", r.stats.movingS > 590)
        assertEquals(144_000.0, r.points.last().distanceM, 1_500.0)
        assertTrue(r.autoLaps.isEmpty())
        assertNull(r.calories)
        assertEquals(300.0, r.stats.elevGainM, 0.0)
        assertEquals(12_000.0, r.stats.maxAltM!!, 0.0)
    }

    @Test
    fun `a walk recalculates to what it recorded, splits included`() {
        val walk = points(1500, 1, 1.4) { 1.4 }
        val laps = listOf(LapEntity(sessionId = "s", kind = "auto", lapIndex = 0, startTime = 0, endTime = 1, distanceM = 1000.0, movingS = 1.0, maxSpeedMps = 1.0, elevGainM = 0.0))
        val r = SessionRecalculator.run(session(ActivityType.Walk), walk, laps)!!
        assertEquals(ActivityType.Walk, r.activity)
        assertEquals(2_100.0, r.stats.distanceM, 50.0)
        assertEquals(900.0, r.calories!!, 0.0)
        // Two whole kilometres plus the 100 m tail.
        assertEquals(listOf(1000.0, 1000.0), r.autoLaps.take(2).map { it.distanceM })
        assertEquals(3, r.autoLaps.size)
    }

    @Test
    fun `a stop stays a stop`() {
        // Speed 0 with positions not moving is a real stop, not a re-anchored fix.
        val parked = points(120, 1, 0.0) { 0.0 }
        val r = SessionRecalculator.run(session(ActivityType.Drive), parked, emptyList())!!
        assertEquals(0.0, r.stats.distanceM, 1.0)
        assertEquals(0.0, r.stats.maxSpeedMps, 0.01)
    }
}
