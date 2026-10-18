package com.shivarya.locationspeedtracker.core.migration

import com.shivarya.locationspeedtracker.core.data.db.SessionSource
import com.shivarya.locationspeedtracker.core.data.db.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySessionMapperTest {
    private fun point(t: Long, lat: Double, acc: Double = 5.0, speed: Double = 1.4, extra: String = "") =
        """{"latitude":$lat,"longitude":77.59,"altitude":920.5,"speed":$speed,"accuracy":$acc,"heading":0,"timestamp":$t$extra}"""

    @Test
    fun `maps sessions written by the Expo app`() {
        val base = 1_700_000_000_000L
        val step = 1.4 / 111_195.0
        val pts = (0..120).map { point(base + it * 1000L, 12.97 + it * step) }.toMutableList()
        // Out of order, a duplicate (same fix stored by both Expo streams), an unknown-accuracy point and a
        // 1.0.7-style "source" field.
        pts.add(pts[5])
        pts.reverse()
        pts[10] = point(base + 200_000, 12.97 + 121 * step, acc = 0.0, extra = ""","source":"background"""")
        val json = """[
            {"id":"a1","startTime":$base,"endTime":${base + 125_000},"points":[${pts.joinToString(",")}],
             "statistics":{"totalDistance":999,"avgSpeed":1,"maxSpeed":2,"minSpeed":0.5,"duration":125}},
            null,
            {"id":"empty","startTime":${base + 1_000_000},"points":[],
             "statistics":{"totalDistance":1234.5,"avgSpeed":3,"maxSpeed":7.5,"minSpeed":1,"duration":400}},
            {"startTime":5,"points":[]}
        ]"""
        val sessions = LegacySessionMapper.parse(json)
        assertEquals(2, sessions.size)

        val a = sessions.first { it.session.id == "a1" }
        assertEquals(SessionSource.Legacy.stored, a.session.source)
        assertEquals(SessionState.Finished.stored, a.session.state)
        assertEquals(125.0, a.session.elapsedS, 0.01)
        // Recomputed by the native filter: ~120 s of walking at 1.4 m/s.
        assertEquals(168.0, a.session.stats.distanceM, 20.0)
        assertTrue(a.points.zipWithNext().all { (p, q) -> p.time < q.time })
        assertTrue("points ${a.points.size}", a.points.size in 115..123)
        assertNotNull(a.session.startLat)

        // No points: the Expo app's own totals are kept.
        val e = sessions.first { it.session.id == "empty" }
        assertEquals(1234.5, e.session.stats.distanceM, 0.01)
        assertEquals(7.5, e.session.stats.maxSpeedMps, 0.01)
        assertEquals(400.0, e.session.elapsedS, 0.01)
    }

    @Test
    fun `garbage coordinates are dropped`() {
        val fixes = LegacySessionMapper.cleanPoints(
            listOf(
                LegacyPoint(0.0, 0.0, timestamp = 1.0),
                LegacyPoint(91.0, 10.0, timestamp = 2.0),
                LegacyPoint(10.0, 10.0, timestamp = null),
                LegacyPoint(10.0, 10.0, timestamp = 3.0),
            ),
        )
        assertEquals(1, fixes.size)
    }
}
