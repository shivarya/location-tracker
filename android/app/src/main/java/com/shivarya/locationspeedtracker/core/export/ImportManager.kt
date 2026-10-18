package com.shivarya.locationspeedtracker.core.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.shivarya.locationspeedtracker.core.data.FullSession
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionSource
import com.shivarya.locationspeedtracker.core.data.db.SessionState
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.tracking.FilterConfig
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.PointFilter
import com.shivarya.locationspeedtracker.core.tracking.StatsAccumulator
import com.shivarya.locationspeedtracker.core.tracking.Verdict
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val imported: Int, val skipped: Int, val message: String)

@Singleton
class ImportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
) {
    /** Imports a SpeedTrack JSON export or a GPX file chosen through the Storage Access Framework. */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Can't open the file")
            if (bytes.size > MAX_BYTES) error("File is too large (over ${MAX_BYTES / 1_000_000} MB)")
            val head = String(bytes, 0, minOf(bytes.size, 512)).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            when {
                head.startsWith("{") -> importJson(String(bytes))
                head.startsWith("<") && head.contains("gpx", ignoreCase = true) -> importGpx(bytes, displayName(uri))
                else -> error("Not a GPX or SpeedTrack JSON file")
            }
        }.getOrElse {
            Log.w(TAG, "Import failed", it)
            ImportResult(0, 0, "Import failed: ${it.message}")
        }
    }

    private suspend fun importJson(text: String): ImportResult {
        val doc = ExportJson.decodeFromString(ExportDocument.serializer(), text)
        require(doc.format == ExportDocument.FORMAT) { "Unknown JSON format" }
        var imported = 0
        var skipped = 0
        for (s in doc.sessions) {
            val finished = s.toFull().let { it.copy(session = it.session.copy(deletedAt = null)) }
            if (sessions.import(finished)) imported++ else skipped++
        }
        return ImportResult(imported, skipped, summary(imported, skipped))
    }

    private suspend fun importGpx(bytes: ByteArray, fileName: String?): ImportResult {
        val track = GpxParser.parse(bytes.inputStream())
        val full = toSession(track, fileName) ?: error("The GPX file has no track points")
        val ok = sessions.import(full)
        return ImportResult(if (ok) 1 else 0, if (ok) 0 else 1, if (ok) "Imported “${full.session.name}”" else "Already imported")
    }

    private fun summary(imported: Int, skipped: Int) = buildString {
        append("Imported $imported session${if (imported == 1) "" else "s"}")
        if (skipped > 0) append(" ($skipped already present)")
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        private const val TAG = "ImportManager"
        private const val MAX_BYTES = 80_000_000

        /** Builds a session from a parsed GPX: replayed through the filter (lenient — GPX has no accuracy). */
        fun toSession(track: GpxTrack, fileName: String?): FullSession? {
            val all = track.segments.flatten()
            if (all.isEmpty()) return null
            val activity = activityFor(track.type)
            val id = UUID.randomUUID().toString()
            // Route files have no timestamps: space points one second apart so ordering and charts still work.
            val timed = all.any { it.time != null }
            val base = all.firstNotNullOfOrNull { it.time } ?: System.currentTimeMillis()
            val filter = PointFilter(
                FilterConfig(
                    maxAccuracyM = 1000.0,
                    missingAccuracyM = 5.0,
                    minIntervalMs = 0,
                    // Synthetic 1 s spacing makes speeds meaningless, so don't reject on them.
                    maxSpeedMps = if (timed) activity.maxValidSpeedMps else Double.MAX_VALUE,
                    maxAccelMps2 = if (timed) 12.0 else Double.MAX_VALUE,
                    movingThresholdMps = activity.movingThresholdMps,
                    noiseFloorM = 1.0,
                ),
            )
            val acc = StatsAccumulator(activity.movingThresholdMps)
            val points = ArrayList<TrackPointEntity>(all.size)
            val metrics = ArrayList<MetricEntity>()
            var counter = 0L
            track.segments.forEachIndexed { segIndex, seg ->
                filter.reset()
                acc.breakSegment()
                for (p in seg) {
                    val t = p.time ?: (base + counter * 1000)
                    counter++
                    val fix = Fix(time = t, lat = p.lat, lon = p.lon, altitude = p.ele, speed = p.speed)
                    when (val v = filter.offer(fix)) {
                        is Verdict.Rejected -> acc.onRejected()
                        is Verdict.Accepted -> {
                            acc.onAccepted(fix, v)
                            points += TrackPointEntity(
                                sessionId = id,
                                seq = points.size,
                                time = t,
                                lat = p.lat,
                                lon = p.lon,
                                altitudeMsl = p.ele,
                                speed = v.speedMps,
                                segment = segIndex,
                                distanceM = acc.stats.distanceM,
                                provider = "gpx",
                            )
                            if (p.hr != null || p.cad != null) {
                                metrics += MetricEntity(sessionId = id, time = t / 1000 * 1000, heartRate = p.hr, cadence = p.cad?.let { it * 2f })
                            }
                        }
                    }
                }
            }
            if (points.isEmpty()) return null
            val start = points.first().time
            val end = points.last().time
            val hrs = metrics.mapNotNull { it.heartRate }
            val name = track.name ?: fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported track"
            val session = SessionEntity(
                id = id,
                startTime = start,
                endTime = end,
                activity = activity.stored,
                name = name,
                source = SessionSource.Import.stored,
                state = SessionState.Finished.stored,
                elapsedS = (end - start) / 1000.0,
                stats = acc.stats,
                avgHr = hrs.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                maxHr = hrs.maxOrNull(),
                startLat = points.first().lat,
                startLon = points.first().lon,
            )
            return FullSession(session, points, emptyList(), metrics.distinctBy { it.time })
        }

        fun activityFor(type: String?): ActivityType {
            val t = type?.lowercase() ?: return ActivityType.Other
            return when {
                "run" in t -> ActivityType.Run
                "walk" in t -> ActivityType.Walk
                "hik" in t -> ActivityType.Hike
                "cycl" in t || "bik" in t || "ride" in t -> ActivityType.Cycle
                "motor" in t -> ActivityType.Motorbike
                "driv" in t || "car" in t -> ActivityType.Drive
                else -> ActivityType.Other
            }
        }
    }
}
