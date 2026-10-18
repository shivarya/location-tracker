package com.shivarya.locationspeedtracker.core.migration

import com.shivarya.locationspeedtracker.core.data.FullSession
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionSource
import com.shivarya.locationspeedtracker.core.data.db.SessionState
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.geo.Geo
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.tracking.FilterConfig
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.PointFilter
import com.shivarya.locationspeedtracker.core.tracking.SessionStats
import com.shivarya.locationspeedtracker.core.tracking.StatsAccumulator
import com.shivarya.locationspeedtracker.core.tracking.Verdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** The Expo app's `TrackingSession` (src/store/types.ts). Everything optional: old builds wrote sparse objects. */
@Serializable
internal data class LegacySession(
    val id: String? = null,
    val startTime: Double? = null,
    val endTime: Double? = null,
    val points: List<LegacyPoint> = emptyList(),
    val statistics: LegacyStats? = null,
)

/** `LocationPoint`; 1.0.7 builds also wrote `source` ("foreground" | "background"), ignored. */
@Serializable
internal data class LegacyPoint(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val speed: Double? = null,
    val accuracy: Double? = null,
    val heading: Double? = null,
    val timestamp: Double? = null,
)

@Serializable
internal data class LegacyStats(
    val totalDistance: Double? = null,
    val avgSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val minSpeed: Double? = null,
    val duration: Double? = null,
)

/**
 * Converts the Expo app's `tracking_sessions` JSON into native sessions. Points are sorted and de-duplicated (the
 * Expo app merged foreground and background fixes into the same list), then replayed through the native filter so
 * imported stats are computed the same way as new ones. The old app wrote `accuracy: 0` for "unknown" and recorded
 * with no filtering, so the import is lenient: only clear garbage (bad coordinates, impossible jumps) is dropped.
 */
object LegacySessionMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val importFilter = FilterConfig(
        maxAccuracyM = 100.0,
        missingAccuracyM = 20.0,
        maxSpeedMps = ActivityType.Other.maxValidSpeedMps,
        movingThresholdMps = ActivityType.Other.movingThresholdMps,
    )

    fun parse(raw: String): List<FullSession> {
        val sessions = json.decodeFromString<List<LegacySession?>>(raw).filterNotNull()
        return sessions.mapNotNull { runCatching { map(it) }.getOrNull() }
    }

    internal fun map(s: LegacySession): FullSession? {
        val id = s.id?.takeIf { it.isNotBlank() } ?: return null
        val fixes = cleanPoints(s.points)
        val start = s.startTime?.toLong() ?: fixes.firstOrNull()?.time ?: return null
        val end = s.endTime?.toLong() ?: fixes.lastOrNull()?.time ?: start

        val filter = PointFilter(importFilter)
        val acc = StatsAccumulator(ActivityType.Other.movingThresholdMps)
        val points = ArrayList<TrackPointEntity>(fixes.size)
        for (f in fixes) {
            when (val v = filter.offer(f)) {
                is Verdict.Rejected -> acc.onRejected()
                is Verdict.Accepted -> {
                    acc.onAccepted(f, v)
                    points += TrackPointEntity(
                        sessionId = id,
                        seq = points.size,
                        time = f.time,
                        lat = f.lat,
                        lon = f.lon,
                        altitude = f.altitude,
                        speed = v.speedMps,
                        bearing = f.bearing,
                        hAcc = f.hAcc,
                        distanceM = acc.stats.distanceM,
                        provider = "legacy",
                    )
                }
            }
        }
        // No usable points (or none at all): keep the Expo app's own totals so the history row still means something.
        val stats = if (points.isEmpty()) {
            val st = s.statistics
            SessionStats(
                distanceM = st?.totalDistance?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                maxSpeedMps = st?.maxSpeed?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                minMovingSpeedMps = st?.minSpeed?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                movingS = st?.duration?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
            )
        } else acc.stats
        val elapsedS = ((end - start) / 1000.0).coerceAtLeast(0.0).let {
            if (it == 0.0) s.statistics?.duration?.takeIf { d -> d.isFinite() && d > 0 } ?: 0.0 else it
        }
        val session = SessionEntity(
            id = id,
            startTime = start,
            endTime = end,
            activity = ActivityType.Other.stored,
            source = SessionSource.Legacy.stored,
            state = SessionState.Finished.stored,
            elapsedS = elapsedS,
            stats = stats,
            startLat = points.firstOrNull()?.lat,
            startLon = points.firstOrNull()?.lon,
            updatedAt = end,
        )
        return FullSession(session, points, emptyList(), emptyList())
    }

    /** Valid coordinates only, time-ordered, with duplicate fixes (<0.5 s and ≤2 m apart) removed. */
    internal fun cleanPoints(raw: List<LegacyPoint>): List<Fix> {
        val fixes = raw.mapNotNull { p ->
            val lat = p.latitude ?: return@mapNotNull null
            val lon = p.longitude ?: return@mapNotNull null
            val t = p.timestamp ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite() || abs(lat) > 90 || abs(lon) > 180) return@mapNotNull null
            if (lat == 0.0 && lon == 0.0) return@mapNotNull null
            Fix(
                time = t.toLong(),
                lat = lat,
                lon = lon,
                altitude = p.altitude?.takeIf { it.isFinite() && it != 0.0 },
                // The Expo app wrote `speed || 0`, so 0 means "unknown" as often as "stopped".
                speed = p.speed?.takeIf { it.isFinite() && it > 0 },
                bearing = p.heading?.takeIf { it.isFinite() && it >= 0 },
                hAcc = p.accuracy?.takeIf { it.isFinite() && it > 0 },
            )
        }.sortedBy { it.time }
        val out = ArrayList<Fix>(fixes.size)
        for (f in fixes) {
            val last = out.lastOrNull()
            // The Expo app's foreground (1 s) and background (5 s) streams both stored the same fused fixes. Only
            // near-identical pairs are duplicates — 1.0.7's 1.2 s / 3 m rule also deleted every other 1 Hz walking fix.
            if (last != null && f.time - last.time < 500 && Geo.distance(last.lat, last.lon, f.lat, f.lon) <= 2.0) continue
            out += f
        }
        return out
    }
}
