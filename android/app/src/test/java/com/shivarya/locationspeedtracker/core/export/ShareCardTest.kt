package com.shivarya.locationspeedtracker.core.export

import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.tracking.SessionStats
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ShareCardTest {
    /** A straight route north from the equator, one sample every [step] metres. */
    private fun route(lengthM: Double, step: Double = 50.0) = (0..(lengthM / step).toInt()).map { i ->
        val d = i * step
        TrackSample(time = i * 10_000L, lat = d / 111_320.0, lon = 0.0, speedMps = 5.0, altitude = null, segment = 0, distanceM = d)
    }

    @Test
    fun `hide start and finish trims 200 m from each end, at most a quarter of the route`() {
        val long = trimEnds(route(2_000.0))
        assertEquals(200.0, long.first().distanceM, 1e-9)
        assertEquals(1_800.0, long.last().distanceM, 1e-9)

        val short = trimEnds(route(400.0))
        assertEquals(100.0, short.first().distanceM, 1e-9)
        assertEquals(300.0, short.last().distanceM, 1e-9)

        val one = route(0.0)
        assertEquals(one, trimEnds(one))
    }

    @Test
    fun `route fits inside the padded box, north up, without stretching`() {
        // A 1 km × 1 km square at the equator: equal Mercator spans, so it must come out square.
        val deg = 1_000.0 / 111_320.0
        val pts = listOf(0.0 to 0.0, 0.0 to deg, deg to deg, deg to 0.0)
        val xy = fitRoute(pts, 1080f, 800f, 100f)
        xy.forEach {
            assertTrue(it.x in 100f..980f)
            assertTrue(it.y in 100f..700f)
        }
        val w = xy.maxOf { it.x } - xy.minOf { it.x }
        val h = xy.maxOf { it.y } - xy.minOf { it.y }
        assertEquals(w, h, 1f)
        assertEquals(600f, h, 1f) // the height is the limiting side: 800 − 2 × 100
        assertTrue("north is up", xy[2].y < xy[1].y)

        val single = fitRoute(listOf(12.97 to 77.59), 1080f, 800f, 100f).single()
        assertEquals(540f, single.x, 0.01f)
        assertEquals(400f, single.y, 0.01f)
    }

    private fun session(activity: String, distanceM: Double, movingS: Double, maxMps: Double, gainM: Double, calories: Double? = null) =
        SessionEntity(
            id = "s",
            startTime = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 18, 7, 0, 0) }.timeInMillis,
            activity = activity,
            elapsedS = movingS + 60,
            stats = SessionStats(distanceM = distanceM, movingS = movingS, maxSpeedMps = maxMps, elevGainM = gainM),
            calories = calories,
        )

    @Test
    fun `card stats - a run shows pace, a drive shows speed`() {
        val run = cardStats(session("run", 5_210.0, 1_694.0, 4.2, 38.0, calories = 410.0), UnitSystem.Metric, showPace = true)
        assertEquals(listOf("Distance", "Moving time", "Avg pace", "Max speed", "Climb", "Calories"), run.map { it.label })
        assertEquals(StatTile("Distance", "5.21", "km"), run[0])
        assertEquals("28:14", run[1].value)
        assertEquals(StatTile("Avg pace", "5:25", "/km"), run[2]) // 5.21 km in 28:14

        val drive = cardStats(session("drive", 32_187.0, 1_800.0, 30.0, 120.0), UnitSystem.Imperial, showPace = true)
        assertEquals("Avg speed", drive[2].label)
        assertEquals("mph", drive[2].unit)
        assertEquals(StatTile("Distance", "20.0", "mi"), drive[0])
        assertEquals(5, drive.size) // no calories, heart rate or steps
    }

    @Test
    fun `share text sums the session up in one line`() {
        val text = shareSummary(session("run", 5_210.0, 1_694.0, 4.2, 38.0), UnitSystem.Metric, showPace = true)
        assertEquals("Morning run · 5.21 km in 28:14 · avg 5:25 /km — SpeedTrack", text)
    }
}
