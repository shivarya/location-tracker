package com.shivarya.locationspeedtracker.core

import com.shivarya.locationspeedtracker.core.fitness.CalorieCalculator
import com.shivarya.locationspeedtracker.core.fitness.HrZones
import com.shivarya.locationspeedtracker.core.geo.Bounds
import com.shivarya.locationspeedtracker.core.hr.HrParser
import com.shivarya.locationspeedtracker.core.location.Nmea
import com.shivarya.locationspeedtracker.core.maps.TileEstimator
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.sensors.BaroAltimeter
import com.shivarya.locationspeedtracker.core.sensors.CadenceMeter
import com.shivarya.locationspeedtracker.core.sensors.MotionMath
import com.shivarya.locationspeedtracker.core.settings.Profile
import com.shivarya.locationspeedtracker.core.settings.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorsAndFitnessTest {
    @Test
    fun `heart-rate packets - uint8, uint16, contact and RR intervals`() {
        assertEquals(72, HrParser.parse(byteArrayOf(0x00, 72))!!.bpm)
        // UINT16 = 0x012C = 300? use 0x00B4 = 180
        assertEquals(180, HrParser.parse(byteArrayOf(0x01, 0xB4.toByte(), 0x00))!!.bpm)
        val withContact = HrParser.parse(byteArrayOf(0x06, 90))!!
        assertEquals(true, withContact.contact)
        assertNull(HrParser.parse(byteArrayOf(0x00, 90))!!.contact)
        // RR present: 1024 ticks = 1000 ms
        val rr = HrParser.parse(byteArrayOf(0x10, 60, 0x00, 0x04))!!
        assertEquals(listOf(1000.0), rr.rrMs)
        assertNull(HrParser.parse(byteArrayOf()))
    }

    @Test
    fun `NMEA GSA dilution of precision`() {
        val dop = Nmea.parseGsa("\$GNGSA,A,3,05,07,13,15,18,20,24,,,,,,1.6,0.9,1.3,1*0C")!!
        assertEquals(1.6, dop.pdop!!, 1e-9)
        assertEquals(0.9, dop.hdop!!, 1e-9)
        assertEquals(1.3, dop.vdop!!, 1e-9)
        assertNull(Nmea.parseGsa("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"))
    }

    @Test
    fun `vehicle-frame G from world acceleration`() {
        // Heading east (90°), accelerating east at 1 g → +1 longitudinal, 0 lateral.
        val (long, lat) = MotionMath.vehicleG(9.80665f, 0f, 90.0)
        assertEquals(1f, long, 1e-4f)
        assertEquals(0f, lat, 1e-4f)
        // Heading north, pushed east (right) → +lateral.
        val (l2, t2) = MotionMath.vehicleG(4.9f, 0f, 0.0)
        assertEquals(0f, l2, 1e-4f)
        assertEquals(0.5f, t2, 1e-3f)
    }

    @Test
    fun `barometric altitude and calibration`() {
        assertEquals(0f, MotionMath.pressureAltitude(1013.25f), 0.01f)
        assertEquals(110.9f, MotionMath.pressureAltitude(1000f), 0.5f)
        val b = BaroAltimeter(calibrationAlpha = 1f, smoothAlpha = 1f)
        b.onPressure(1000f, 0)
        repeat(3) { b.onGnssAltitude(920.0, 3.0) }
        assertTrue(b.calibrated)
        assertEquals(920f, b.altitude!!, 0.1f)
        b.onGnssAltitude(100.0, 30.0) // inaccurate: ignored
        assertEquals(920f, b.altitude!!, 0.1f)
    }

    @Test
    fun `cadence from step timestamps`() {
        val c = CadenceMeter()
        (0 until 20).forEach { c.onStep(it * 375L) } // 160 spm
        assertEquals(160f, c.cadence(7_200), 1f)
    }

    @Test
    fun `calories - walking, running, heart rate, motorised`() {
        val p = Profile(weightKg = 70.0, sex = Sex.Male)
        val walk = CalorieCalculator.kcalPerMinute(ActivityType.Walk, 1.4, 0.0, null, p, 35)
        assertEquals(4.2, walk, 0.3) // ~250 kcal/h
        val run = CalorieCalculator.kcalPerMinute(ActivityType.Run, 3.0, 0.0, null, p, 35)
        assertEquals(13.8, run, 0.6)
        val hr = CalorieCalculator.kcalPerMinute(ActivityType.Run, 3.0, 0.0, 150, p, 35)
        assertEquals(13.6, hr, 1.0)
        assertEquals(0.0, CalorieCalculator.kcalPerMinute(ActivityType.Drive, 20.0, 0.0, null, p, 35), 0.0)
    }

    @Test
    fun `heart-rate zones`() {
        assertEquals(0, HrZones.zone(80, 190))
        assertEquals(1, HrZones.zone(100, 190))
        assertEquals(3, HrZones.zone(140, 190))
        assertEquals(5, HrZones.zone(185, 190))
    }

    @Test
    fun `tile estimates and the per-area cap`() {
        assertEquals(1L, TileEstimator.tiles(Bounds(-80.0, -179.0, 80.0, 179.0), 0, 0))
        // ~20 × 20 km around Bengaluru fits; all of India doesn't.
        assertTrue(TileEstimator.fits(Bounds(12.88, 77.50, 13.06, 77.68)))
        assertFalse(TileEstimator.fits(Bounds(8.0, 68.0, 35.0, 97.0)))
    }
}
