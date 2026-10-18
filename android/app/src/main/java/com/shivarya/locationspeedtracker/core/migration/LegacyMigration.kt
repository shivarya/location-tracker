package com.shivarya.locationspeedtracker.core.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads AsyncStorage v1's SQLite store (`RKStorage`, table `catalystLocalStorage(key, value)`).
 *
 * Values are read in [CHUNK]-character slices with substr(): the Expo app kept *every* session with every GPS point
 * in the single `tracking_sessions` value, which easily passes the ~2 MB CursorWindow limit. A plain getString()
 * throws SQLiteBlobTooBigException there — and since the error is swallowed, the user's history would silently
 * vanish (expense-tracker/diet-plan's migration reads small values and never hit this).
 */
class RkStorageReader(private val file: File) {
    fun read(keys: List<String>): Map<String, String> {
        if (!file.exists()) return emptyMap()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            return buildMap {
                for (key in keys) readValue(db, key)?.let { put(key, it) }
            }
        }
    }

    private fun readValue(db: SQLiteDatabase, key: String): String? {
        val length = db.rawQuery("SELECT length(value) FROM catalystLocalStorage WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        } ?: return null
        val sb = StringBuilder(length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        var start = 1L
        while (start <= length) {
            db.rawQuery(
                "SELECT substr(value, ?, ?) FROM catalystLocalStorage WHERE key = ?",
                arrayOf(start.toString(), CHUNK.toString(), key),
            ).use { c -> if (c.moveToFirst()) sb.append(c.getString(0)) }
            start += CHUNK
        }
        return sb.toString()
    }

    companion object {
        /** Characters per read; UTF-16 in the CursorWindow → ~1 MB worst case, well under its 2 MB default. */
        const val CHUNK = 500_000
    }
}

/**
 * One-time import of the Expo app this build replaces (same package, installed as an update): every saved session
 * from `tracking_sessions`, plus the `unit_preference` choice. `RKStorage` is left untouched as a rollback path.
 * Ignored keys: `background_locations*` / `active_tracking_session_id` (in-flight buffers whose points the old app
 * already merged or lost) and `offline_map_areas` (1.0.7's Google-cache warm-up, replaced by real offline maps).
 */
@Singleton
class LegacyMigration @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
) {
    suspend fun runIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (settings.isLegacyMigrated()) return@withContext 0
        val file = context.getDatabasePath(DB_NAME)
        if (!file.exists()) {
            settings.applyLegacy(unitPreference = null, importedSessions = 0)
            return@withContext 0
        }
        if (settings.legacyAttempts() >= MAX_ATTEMPTS) {
            Log.w(TAG, "Giving up on the legacy import after $MAX_ATTEMPTS attempts")
            settings.applyLegacy(unitPreference = null, importedSessions = 0)
            return@withContext 0
        }
        settings.recordLegacyAttempt()
        try {
            val values = RkStorageReader(file).read(listOf(KEY_SESSIONS, KEY_UNITS))
            val parsed = values[KEY_SESSIONS]?.let { LegacySessionMapper.parse(it) }.orEmpty()
            var imported = 0
            for (s in parsed) if (sessions.import(s)) imported++
            settings.applyLegacy(unitPreference = values[KEY_UNITS], importedSessions = imported)
            Log.i(TAG, "Legacy migration done ($imported sessions imported, ${values.keys.size} keys read)")
            imported
        } catch (e: Exception) {
            // Not marked done: the next launch retries (bounded by MAX_ATTEMPTS).
            Log.e(TAG, "Could not import the Expo app's data", e)
            0
        }
    }

    companion object {
        private const val TAG = "LegacyMigration"
        const val DB_NAME = "RKStorage"
        const val KEY_SESSIONS = "tracking_sessions"
        const val KEY_UNITS = "unit_preference"
        private const val MAX_ATTEMPTS = 3
    }
}
