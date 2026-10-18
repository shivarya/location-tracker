package com.shivarya.locationspeedtracker.core.units

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun speeds() {
        assertEquals(100.0, Units.speed(100 / 3.6, UnitSystem.Metric).value, 1e-9)
        assertEquals(60.0, Units.speed(26.8224, UnitSystem.Imperial).value, 0.001)
        assertEquals(26.8224, Units.speedToMps(60.0, UnitSystem.Imperial), 0.001)
    }

    @Test
    fun distancesAndAltitude() {
        assertEquals(1.0, Units.distance(1609.344, UnitSystem.Imperial).value, 1e-9)
        assertEquals(3280.84, Units.altitude(1000.0, UnitSystem.Imperial).value, 0.01)
        assertEquals("12.4 km", Format.distance(12_400.0, UnitSystem.Metric).replace(',', '.'))
    }

    @Test
    fun pace() {
        // 5:00 /km = 3.333 m/s
        assertEquals("5:00 /km", Format.pace(1000.0 / 300, UnitSystem.Metric))
        assertEquals("8:03 /mi", Format.pace(1609.344 / 483, UnitSystem.Imperial))
        assertEquals("–:––", Format.paceValue(0.0, UnitSystem.Metric))
    }

    @Test
    fun durationsAndCompass() {
        assertEquals("0:59", Format.duration(59.4))
        assertEquals("1:02:03", Format.duration(3723.0))
        assertEquals("NE", Format.compass(44.0))
        assertEquals("N", Format.compass(359.0))
    }
}
