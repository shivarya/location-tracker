package com.shivarya.locationspeedtracker.core.sync

import com.shivarya.locationspeedtracker.core.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceNamerTest {
    private val blr = 13.199 to 77.706
    private val del = 28.556 to 77.100
    private val indiranagar = 12.978 to 77.640
    private val koramangala = 12.935 to 77.624

    private fun name(a: Place?, b: Place?, activity: ActivityType, from: Pair<Double, Double>, to: Pair<Double, Double>) =
        PlaceNamer.routeName(a, b, activity, from, to)

    @Test
    fun `between towns the route is named by town`() {
        assertEquals(
            "Devanahalli → New Delhi flight",
            name(Place("Devanahalli", null, "Karnataka"), Place("New Delhi", "Aerocity", "Delhi"), ActivityType.Flight, blr, del),
        )
        // General journeys have no noun on a route.
        assertEquals("Devanahalli → New Delhi", name(Place("Devanahalli"), Place("New Delhi"), ActivityType.Other, blr, del))
    }

    @Test
    fun `within one town the route is named by neighbourhood`() {
        assertEquals(
            "Indiranagar → Koramangala drive",
            name(Place("Bengaluru", "Indiranagar"), Place("Bengaluru", "Koramangala"), ActivityType.Drive, indiranagar, koramangala),
        )
        assertEquals("Indiranagar → Koramangala ride", name(Place("Bengaluru", "Indiranagar"), Place("Bengaluru", "Koramangala"), ActivityType.Cycle, indiranagar, koramangala))
    }

    @Test
    fun `a loop is named after its start`() {
        val near = indiranagar.first + 0.005 to indiranagar.second
        assertEquals("Indiranagar run", name(Place("Bengaluru", "Indiranagar"), Place("Bengaluru", "HAL 2nd Stage"), ActivityType.Run, indiranagar, near))
        assertEquals("Indiranagar trip", name(Place("Bengaluru", "Indiranagar"), Place("Bengaluru", "Indiranagar"), ActivityType.Other, indiranagar, near))
        // Same neighbourhood at both ends of a longer route: still one place.
        assertEquals("Bengaluru walk", name(Place("Bengaluru"), Place("Bengaluru"), ActivityType.Walk, indiranagar, koramangala))
    }

    @Test
    fun `one end unknown falls back to the known one, none gives no name`() {
        assertEquals("Mumbai train", name(Place("Mumbai"), null, ActivityType.Train, blr, del))
        assertEquals("Kerala trip", name(null, Place(adminArea = "Kerala"), ActivityType.Other, blr, del))
        assertNull(name(null, null, ActivityType.Run, blr, del))
    }
}
