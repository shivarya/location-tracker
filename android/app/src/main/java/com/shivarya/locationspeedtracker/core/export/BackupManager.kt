package com.shivarya.locationspeedtracker.core.export

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shivarya.locationspeedtracker.BuildConfig
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.data.db.PerfRunDao
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full backup to a single ZIP (one JSON document: every session with points, laps and sensor rows, performance
 * runs, settings) written wherever the user picks through the Storage Access Framework — Drive, Downloads, a USB
 * stick. No storage permission needed. Restoring merges: sessions already present are skipped.
 */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val perfRuns: PerfRunDao,
    private val settings: SettingsRepository,
) {
    fun suggestedName() = "speedtrack-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.zip"

    suspend fun backup(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val all = sessions.finishedList().mapNotNull { sessions.full(it.id)?.toExport() }
            val doc = ExportDocument(
                exportedAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                sessions = all,
                perfRuns = perfRuns.all(),
                settings = settings.exportMap(),
            )
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(ENTRY))
                    zip.write(ExportJson.encodeToString(ExportDocument.serializer(), doc).toByteArray())
                    zip.closeEntry()
                }
            } ?: error("Can't write to that location")
            all.size
        }.onFailure { Log.w(TAG, "Backup failed", it) }
    }

    suspend fun restore(source: Uri): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    generateSequence { zip.nextEntry }.firstOrNull { it.name == ENTRY } ?: error("Not a SpeedTrack backup")
                    zip.readBytes().toString(Charsets.UTF_8)
                }
            } ?: error("Can't open the file")
            val doc = ExportJson.decodeFromString(ExportDocument.serializer(), json)
            require(doc.format == ExportDocument.FORMAT) { "Not a SpeedTrack backup" }
            var imported = 0
            var skipped = 0
            for (s in doc.sessions) if (sessions.import(s.toFull())) imported++ else skipped++
            val known = perfRuns.all().map { it.time to it.mode }.toSet()
            perfRuns.insertAll(doc.perfRuns.filter { (it.time to it.mode) !in known }.map { it.copy(id = 0) })
            if (doc.settings.isNotEmpty()) settings.restoreMap(doc.settings)
            ImportResult(imported, skipped, "Restored $imported session${if (imported == 1) "" else "s"}" + if (skipped > 0) " ($skipped already here)" else "")
        }.onFailure { Log.w(TAG, "Restore failed", it) }
    }

    private companion object {
        const val TAG = "Backup"
        const val ENTRY = "speedtrack-backup.json"
    }
}
