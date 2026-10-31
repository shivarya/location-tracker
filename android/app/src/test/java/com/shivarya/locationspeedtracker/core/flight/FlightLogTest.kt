package com.shivarya.locationspeedtracker.core.flight

import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightLogTest {
    private val t0 = 1_700_000_000_000L
    private val metresPerDegLat = 111_195.0

    /** A 1 Hz flight north from Bengaluru, built phase by phase: (seconds, speed m/s, vertical speed m/s). */
    private fun flight(vararg legs: Triple<Int, Double, Double>, gapAfter: Int? = null, gapS: Int = 0): List<TrackSample> {
        val out = ArrayList<TrackSample>()
        var t = 0L
        var dist = 0.0
        var alt = 900.0
        legs.forEachIndexed { i, (secs, speed, vs) ->
            repeat(secs) {
                t += 1
                dist += speed
                alt += vs
                out += TrackSample(t0 + t * 1000, 13.2 + dist / metresPerDegLat, 77.7, speed, alt, 0, dist)
            }
            if (i == gapAfter) {
                // GPS lost behind the window: the aircraft keeps flying, nothing is recorded.
                t += gapS
                dist += speed * gapS
            }
        }
        return out
    }

    private val gate = Triple(300, 0.0, 0.0)
    private val taxi = Triple(300, 8.0, 0.0)
    private val roll = Triple(40, 50.0, 0.0)
    private val climb = Triple(1200, 200.0, 8.5)
    private val cruise = Triple(3600, 240.0, 0.0)
    private val descent = Triple(1200, 180.0, -8.5)
    private val rollout = Triple(30, 30.0, 0.0)

    @Test
    fun `a whole flight finds takeoff, landing, time aloft and cruise`() {
        val takeoffRoll = Triple(20, 70.0, 0.0)
        val f = FlightLog.of(flight(gate, taxi, roll, takeoffRoll, climb, cruise, descent, rollout, taxi, gate))!!
        assertEquals(FlightPhase.Landed, f.phase)
        assertEquals(1, f.legs)
        // Takes off on the first fix of the 70 m/s roll, lands on the first of the 30 m/s roll-out.
        assertEquals(t0 + (300 + 300 + 40 + 1) * 1000L, f.takeoffAt)
        assertEquals(t0 + (300 + 300 + 40 + 20 + 1200 + 3600 + 1200 + 1) * 1000L, f.landingAt)
        assertEquals((20 + 1200 + 3600 + 1200) * 1000L, f.airborneMs)
        assertEquals(240.0, f.cruiseSpeedMps!!, 0.1)
        assertEquals(240.0, f.maxSpeedMps, 0.1)
        assertEquals(900.0 + 1200 * 8.5, f.maxAltitudeM!!, 50.0)
        // 20 × 70 + 1200 × 200 + 3600 × 240 + 1200 × 180 m
        assertEquals(1_321_400.0, f.airborneDistanceM, 100.0)
        assertNull(f.verticalSpeedMps)
    }

    @Test
    fun `phases while airborne come from GNSS vertical speed`() {
        val log = FlightLog()
        val samples = flight(gate, Triple(20, 70.0, 0.0), climb, cruise, descent)
        samples.take(300 + 20 + 600).forEach(log::add)
        assertEquals(FlightPhase.Climb, log.summary().phase)
        assertEquals(8.5, log.summary().verticalSpeedMps!!, 0.5)
        samples.take(300 + 20 + 1200 + 1800).drop(300 + 20 + 600).forEach(log::add)
        assertEquals(FlightPhase.Cruise, log.summary().phase)
        samples.drop(300 + 20 + 1200 + 1800).take(1800 + 600).forEach(log::add)
        assertEquals(FlightPhase.Descent, log.summary().phase)
        assertTrue(log.summary().airborne)
    }

    @Test
    fun `a long GPS dropout in cruise still counts as time in the air`() {
        val takeoffRoll = Triple(20, 70.0, 0.0)
        val f = FlightLog.of(flight(gate, takeoffRoll, climb, cruise, descent, rollout, gapAfter = 3, gapS = 1800))!!
        assertEquals((20 + 1200 + 3600 + 1800 + 1200) * 1000L, f.airborneMs)
        assertTrue(f.airborneDistanceM > 240.0 * (3600 + 1800))
    }

    @Test
    fun `landing during a dropout closes the leg on the first fix back on the ground`() {
        val log = FlightLog()
        flight(gate, Triple(20, 70.0, 0.0), climb).forEach(log::add)
        assertTrue(log.summary().airborne)
        val last = flight(gate, Triple(20, 70.0, 0.0), climb).last()
        log.add(last.copy(time = last.time + 40 * 60_000L, speedMps = 0.0, altitude = 920.0))
        val f = log.summary()
        assertNotNull(f.landingAt)
        assertEquals(FlightPhase.Landed, f.phase)
    }

    @Test
    fun `a stale slow fix after a gap mid-climb is not a landing`() {
        val log = FlightLog()
        val before = flight(gate, Triple(20, 70.0, 0.0), climb)
        before.forEach(log::add)
        val t = before.last().time
        log.add(before.last().copy(time = t + 150_000L, speedMps = 20.0))
        log.add(before.last().copy(time = t + 155_000L, speedMps = 240.0))
        log.add(before.last().copy(time = t + 160_000L, speedMps = 240.0))
        val f = log.summary()
        assertTrue(f.airborne)
        assertEquals(1, f.legs)
    }

    @Test
    fun `a fast drive to the airport is not a takeoff`() {
        val f = FlightLog.of(flight(Triple(1800, 30.0, 0.0), gate))!!
        assertNull(f.takeoffAt)
        assertEquals(0L, f.airborneMs)
        assertEquals(FlightPhase.Gate, f.phase)
    }

    @Test
    fun `a single glitchy fast fix on the ground is not a takeoff`() {
        val samples = flight(gate, taxi).toMutableList()
        samples[400] = samples[400].copy(speedMps = 90.0)
        assertNull(FlightLog.of(samples)!!.takeoffAt)
    }
}
