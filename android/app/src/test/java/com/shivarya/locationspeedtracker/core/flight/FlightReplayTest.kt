package com.shivarya.locationspeedtracker.core.flight

import com.shivarya.locationspeedtracker.core.geo.Geo
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.data.toSample
import com.shivarya.locationspeedtracker.core.tracking.SessionRecalculator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Replays a real flight exported from the app (Session → Export → JSON) through the filter, the stats and the flight
 * log. Exports carry a person's movements, so none is checked in: point SPEEDTRACK_FLIGHT_JSON at one to run it.
 * The export holds only the fixes the old filter accepted, and its re-anchored ones have speed 0 — the case
 * [SessionRecalculator] exists for.
 */
class FlightReplayTest {
    @Test
    fun `a recorded flight replays as one whole flight`() {
        val path = System.getenv("SPEEDTRACK_FLIGHT_JSON")
        assumeTrue("SPEEDTRACK_FLIGHT_JSON not set", path != null && File(path).isFile)
        val export = Json.parseToJsonElement(File(path!!).readText()).jsonObject["sessions"]!!.jsonArray[0].jsonObject
        val json = Json { ignoreUnknownKeys = true }
        val session = json.decodeFromJsonElement(SessionEntity.serializer(), export["session"]!!)
        val points = json.decodeFromJsonElement(ListSerializer(TrackPointEntity.serializer()), export["points"]!!)
        val laps = json.decodeFromJsonElement(ListSerializer(LapEntity.serializer()), export["laps"]!!)

        // What "Recalculate stats" does to the saved session.
        val r = SessionRecalculator.run(session, points, laps)!!
        val s = r.stats
        val flight = FlightLog.of(r.points.map { it.toSample() })
        println(
            "${r.activity}: accepted ${s.acceptedPoints}/${points.size}, distance ${(s.distanceM / 1000).toInt()} km, " +
                "max ${(s.maxSpeedMps * 3.6).toInt()} km/h, moving ${(s.movingS / 60).toInt()} min, " +
                "avg ${(s.avgMovingSpeedMps * 3.6).toInt()} km/h, auto laps ${r.autoLaps.size}",
        )
        println("flight: $flight")
        val direct = Geo.distance(points.first().lat, points.first().lon, points.last().lat, points.last().lon)
        assertEquals(ActivityType.Flight, r.activity)
        assertTrue("distance ${s.distanceM} vs direct $direct", s.distanceM > direct)
        assertTrue("max ${s.maxSpeedMps}", s.maxSpeedMps > 200)
        assertTrue("no auto splits for a flight", r.autoLaps.isEmpty())
        assertNull("no calories for a flight", r.calories)
        assertNotNull(flight?.takeoffAt)
        assertNotNull(flight?.landingAt)
        assertEquals("one takeoff, one landing", 1, flight!!.legs)
    }
}
