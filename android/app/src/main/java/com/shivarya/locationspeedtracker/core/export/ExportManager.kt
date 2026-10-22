package com.shivarya.locationspeedtracker.core.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.shivarya.locationspeedtracker.BuildConfig
import com.shivarya.locationspeedtracker.core.data.FullSession
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.data.displayTitle
import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.PerfRunEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** SpeedTrack's own full-fidelity format — export, backup and (re-)import. */
@Serializable
data class ExportDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val appVersion: String,
    val sessions: List<ExportSession>,
    val perfRuns: List<PerfRunEntity> = emptyList(),
    /** Backups only: DataStore preferences as strings (see BackupManager). */
    val settings: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FORMAT = "speedtrack"
        const val VERSION = 1
    }
}

@Serializable
data class ExportSession(
    val session: SessionEntity,
    val points: List<TrackPointEntity> = emptyList(),
    val laps: List<LapEntity> = emptyList(),
    val metrics: List<MetricEntity> = emptyList(),
) {
    fun toFull() = FullSession(session, points, laps, metrics)
}

fun FullSession.toExport() = ExportSession(session, points, laps, metrics)

val ExportJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

data class ExportFile(val file: File, val mime: String)

@Singleton
class ExportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
) {
    private val dir get() = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    private suspend fun load(ids: List<String>?): List<FullSession> {
        val wanted = ids ?: sessions.finishedList().map { it.id }
        return wanted.mapNotNull { sessions.full(it) }
    }

    suspend fun json(ids: List<String>?): ExportFile = withContext(Dispatchers.IO) {
        val doc = ExportDocument(
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            sessions = load(ids).map { it.toExport() },
        )
        val name = if (ids?.size == 1) "speedtrack-${baseName(doc.sessions.firstOrNull()?.session)}.json" else "speedtrack-${stamp()}.json"
        val f = File(dir, name)
        f.writeText(ExportJson.encodeToString(ExportDocument.serializer(), doc))
        ExportFile(f, "application/json")
    }

    /** One row per session; the first eight columns are the Expo app's CSV layout. */
    suspend fun sessionsCsv(ids: List<String>?): ExportFile = withContext(Dispatchers.IO) {
        val rows = load(ids).map { it.session }
        val f = File(dir, "speedtrack-sessions-${stamp()}.csv")
        f.bufferedWriter().use { w ->
            w.appendLine(
                "Session ID,Start Time,End Time,Duration (s),Total Distance (m),Avg Speed (m/s),Max Speed (m/s),Min Speed (m/s)," +
                    "Name,Activity,Moving Time (s),Elevation Gain (m),Elevation Loss (m),Steps,Avg HR (bpm),Max HR (bpm),Calories (kcal),Max G",
            )
            for (s in rows) {
                val st = s.stats
                w.appendLine(
                    listOf(
                        s.id,
                        Instant.ofEpochMilli(s.startTime).toString(),
                        s.endTime?.let { Instant.ofEpochMilli(it).toString() } ?: "N/A",
                        s.elapsedS.f2(),
                        st.distanceM.f2(),
                        st.avgMovingSpeedMps.f2(),
                        st.maxSpeedMps.f2(),
                        st.minMovingSpeedMps.f2(),
                        csv(s.name),
                        s.activity,
                        st.movingS.f2(),
                        st.elevGainM.f2(),
                        st.elevLossM.f2(),
                        s.steps?.toString().orEmpty(),
                        s.avgHr?.toString().orEmpty(),
                        s.maxHr?.toString().orEmpty(),
                        s.calories?.f2().orEmpty(),
                        s.maxG?.f2().orEmpty(),
                    ).joinToString(","),
                )
            }
        }
        ExportFile(f, "text/csv")
    }

    /** Every recorded point of one session, joined with that second's sensor row when there is one. */
    suspend fun pointsCsv(id: String): ExportFile? = withContext(Dispatchers.IO) {
        val full = sessions.full(id) ?: return@withContext null
        val metrics = full.metrics.associateBy { it.time / 1000 }
        val f = File(dir, "speedtrack-${baseName(full.session)}-points.csv")
        f.bufferedWriter().use { w ->
            w.appendLine(
                "time_iso,time_ms,lat,lon,altitude_m,altitude_msl_m,speed_mps,bearing_deg,h_acc_m,v_acc_m,speed_acc_mps," +
                    "segment,distance_m,provider,satellites,heart_rate_bpm,cadence_spm,g_long,g_lat,pressure_hpa",
            )
            for (p in full.points) {
                val m = metrics[p.time / 1000]
                w.appendLine(
                    listOf(
                        Instant.ofEpochMilli(p.time).toString(), p.time.toString(),
                        p.lat.toString(), p.lon.toString(),
                        p.altitude?.f2().orEmpty(), p.altitudeMsl?.f2().orEmpty(),
                        p.speed?.f2().orEmpty(), p.bearing?.f2().orEmpty(),
                        p.hAcc?.f2().orEmpty(), p.vAcc?.f2().orEmpty(), p.speedAcc?.f2().orEmpty(),
                        p.segment.toString(), p.distanceM.f2(), p.provider.orEmpty(), p.satellites?.toString().orEmpty(),
                        m?.heartRate?.toString().orEmpty(), m?.cadence?.toString().orEmpty(),
                        m?.gLong?.toString().orEmpty(), m?.gLat?.toString().orEmpty(), m?.pressureHpa?.toString().orEmpty(),
                    ).joinToString(","),
                )
            }
        }
        ExportFile(f, "text/csv")
    }

    /** One session in an interchange format (GPX / TCX / KML / per-point CSV). */
    suspend fun track(id: String, format: ExportFormat): ExportFile? =
        withContext(Dispatchers.IO) {
            if (format == ExportFormat.PointsCsv) return@withContext pointsCsv(id)
            val full = sessions.full(id) ?: return@withContext null
            if (full.points.isEmpty()) return@withContext null
            val title = full.session.displayTitle()
            val base = "speedtrack-${baseName(full.session)}"
            when (format) {
                ExportFormat.Gpx ->
                    write("$base.gpx", "application/gpx+xml", TrackFormats.gpx(full, title))
                ExportFormat.Tcx ->
                    write("$base.tcx", "application/vnd.garmin.tcx+xml", TrackFormats.tcx(full))
                ExportFormat.Kml ->
                    write("$base.kml", "application/vnd.google-earth.kml+xml", TrackFormats.kml(full, title))
                else -> null
            }
        }

    fun write(name: String, mime: String, content: String): ExportFile {
        val f = File(dir, name)
        f.writeText(content)
        return ExportFile(f, mime)
    }

    fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Where the share image for [s] is written (cache/exports, shared through the FileProvider). */
    fun imageFile(s: SessionEntity): File = File(dir, "speedtrack-${baseName(s)}.png")

    fun shareIntent(files: List<ExportFile>, subject: String, text: String? = null): Intent {
        val uris = ArrayList(files.map { uri(it.file) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(files[0].mime)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
        }
        send.putExtra(Intent.EXTRA_SUBJECT, subject).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        return Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun baseName(s: SessionEntity?): String {
        if (s == null) return stamp()
        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(s.startTime))
        val label = s.name?.lowercase(Locale.US)?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-')?.take(40)
        return if (label.isNullOrEmpty()) "$date-${ActivityType.from(s.activity).label.lowercase(Locale.US)}" else "$date-$label"
    }
}

private fun Double.f2() = String.format(Locale.US, "%.2f", this)

private fun csv(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

enum class ExportFormat(val label: String) {
    Json("JSON (full data)"),
    Csv("CSV (summary)"),
    Gpx("GPX (Strava, Garmin, Komoot…)"),
    Tcx("TCX (with heart rate)"),
    Kml("KML (Google Earth)"),
    PointsCsv("CSV (every point)"),
}
