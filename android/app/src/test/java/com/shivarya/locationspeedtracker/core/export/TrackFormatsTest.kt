package com.shivarya.locationspeedtracker.core.export

import com.shivarya.locationspeedtracker.core.data.FullSession
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFormatsTest {
    private val t0 = 1_700_000_000_000L
    private val full = FullSession(
        session = SessionEntity(id = "s", startTime = t0, endTime = t0 + 4000, activity = ActivityType.Run.stored, notes = "Fish & chips <3"),
        points = listOf(
            TrackPointEntity("s", 0, t0, 12.97, 77.59, altitudeMsl = 920.0, speed = 3.0, segment = 0),
            TrackPointEntity("s", 1, t0 + 1000, 12.97003, 77.59, altitudeMsl = 921.0, speed = 3.1, segment = 0, distanceM = 3.3),
            TrackPointEntity("s", 2, t0 + 3000, 12.97009, 77.59, altitudeMsl = 922.0, speed = 3.2, segment = 1, distanceM = 9.9),
        ),
        laps = emptyList(),
        metrics = listOf(
            MetricEntity("s", t0, heartRate = 140, cadence = 170f),
            MetricEntity("s", t0 + 1000, heartRate = 142),
            MetricEntity("s", t0 + 3000, heartRate = 150),
        ),
    )

    @Test
    fun `gpx round-trips through the parser with segments and heart rate`() {
        val xml = TrackFormats.gpx(full, "Morning <run>")
        assertTrue(xml.contains("Fish &amp; chips &lt;3"))
        val track = GpxParser.parse(xml.byteInputStream())
        assertEquals("Morning <run>", track.name)
        assertEquals("running", track.type)
        assertEquals(2, track.segments.size)
        assertEquals(listOf(2, 1), track.segments.map { it.size })
        val p = track.segments[0][0]
        assertEquals(12.97, p.lat, 1e-9)
        assertEquals(920.0, p.ele!!, 0.01)
        assertEquals(t0, p.time)
        assertEquals(140, p.hr)
        assertEquals(85, p.cad)
        assertEquals(150, track.segments[1][0].hr)
    }

    @Test
    fun `gpx import builds a finished session`() {
        val track = GpxParser.parse(TrackFormats.gpx(full, "x").byteInputStream())
        val s = ImportManager.toSession(track, "x.gpx")!!
        assertEquals(ActivityType.Run.stored, s.session.activity)
        assertEquals(3, s.points.size)
        assertEquals(setOf(0, 1), s.points.map { it.segment }.toSet())
        assertEquals(150, s.session.maxHr)
    }

    @Test
    fun `route files without timestamps still import`() {
        val gpx = """<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
            <rte><name>Plan</name><rtept lat="1.0" lon="1.0"/><rtept lat="1.001" lon="1.0"/><rtept lat="1.002" lon="1.0"/></rte></gpx>"""
        val track = GpxParser.parse(gpx.byteInputStream())
        val s = ImportManager.toSession(track, null)!!
        assertEquals("Plan", s.session.name)
        assertEquals(3, s.points.size)
        assertEquals(222.0, s.session.stats.distanceM, 5.0)
    }

    @Test
    fun `tcx and kml are well formed enough to contain every point`() {
        val tcx = TrackFormats.tcx(full)
        assertEquals(3, Regex("<Trackpoint>").findAll(tcx).count())
        assertTrue(tcx.contains("<HeartRateBpm><Value>140</Value></HeartRateBpm>"))
        val kml = TrackFormats.kml(full, "x")
        assertEquals(2, Regex("<LineString>").findAll(kml).count())
    }
}
