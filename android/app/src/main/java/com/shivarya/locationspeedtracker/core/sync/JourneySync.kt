package com.shivarya.locationspeedtracker.core.sync

import android.content.Context
import android.util.Log
import com.shivarya.locationspeedtracker.BuildConfig
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.export.ExportDocument
import com.shivarya.locationspeedtracker.core.export.ExportJson
import com.shivarya.locationspeedtracker.core.export.toExport
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Drive sync bookkeeping, kept in its own file (not settings: a settings backup must not carry a Drive account). */
@Serializable
data class SyncState(
    val account: String? = null,
    /** Access was revoked or expired for good: background sync stops until the user reconnects. */
    val needsReconnect: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val journeys: Int = 0,
    /** Sessions this install has uploaded or downloaded, with the version: one missing locally was deleted here. */
    val synced: Map<String, Long> = emptyMap(),
    /** Sessions the geocoder had no name for (open sea, desert): not asked again. */
    val unnameable: Set<String> = emptySet(),
)

/** What one sync pass did; [retry] = a network or server error worth another attempt later. */
data class SyncOutcome(val named: Int = 0, val uploaded: Int = 0, val restored: Int = 0, val deleted: Int = 0, val retry: Boolean = false)

/**
 * Keeps journeys named and mirrored to Google Drive's hidden app folder, one gzipped SpeedTrack JSON per session.
 * Local is the source of truth: new and edited sessions upload, sessions deleted here are deleted there, and sessions
 * only in Drive (a new phone, a reinstall) are restored. Runs in [JourneySyncWorker]; idempotent.
 */
@Singleton
class JourneySync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val auth: DriveAuth,
    private val namer: PlaceNamer,
) {
    private val file = File(context.filesDir, "journey_sync.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    private var namingFailed = false
    private val http = OkHttpClient.Builder().callTimeout(2, TimeUnit.MINUTES).build()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private fun load(): SyncState = runCatching { json.decodeFromString(SyncState.serializer(), file.readText()) }.getOrDefault(SyncState())

    private suspend fun save(transform: (SyncState) -> SyncState) = withContext(Dispatchers.IO) {
        val next = _state.updateAndGet(transform)
        file.writeText(json.encodeToString(SyncState.serializer(), next))
    }

    /** After the consent screen: remember the account; [token] proves access and finds the address. */
    suspend fun connected(token: String) = withContext(Dispatchers.IO) {
        val email = DriveClient(http, token).email() ?: throw IOException("Drive didn't say which account this is")
        save {
            // Another account's Drive knows nothing of what this phone synced before.
            val same = it.account == email
            it.copy(account = email, needsReconnect = false, lastError = null, synced = if (same) it.synced else emptyMap())
        }
    }

    suspend fun disconnect() {
        val account = _state.value.account
        if (account != null) runCatching { auth.revoke(account) }.onFailure { Log.w(TAG, "Revoke failed", it) }
        save { SyncState(unnameable = it.unnameable) }
    }

    suspend fun run(): SyncOutcome = lock.withLock {
        var outcome = SyncOutcome()
        if (settings.current().autoName && namer.available) outcome = outcome.copy(named = nameJourneys(), retry = namingFailed)
        val account = _state.value.account ?: return outcome
        if (_state.value.needsReconnect) return outcome
        val token = try {
            auth.token(account)
        } catch (e: Exception) {
            Log.w(TAG, "Authorization failed", e)
            save { it.copy(lastError = "Couldn't reach your Google account") }
            return outcome.copy(retry = true)
        }
        if (token == null) {
            save { it.copy(needsReconnect = true, lastError = "Drive access was removed — reconnect to keep syncing") }
            return outcome
        }
        return try {
            outcome.merge(mirror(DriveClient(http, token)))
        } catch (e: DriveException) {
            if (e.code == 401) runCatching { auth.clear(token) }
            Log.w(TAG, "Drive sync failed", e)
            save { it.copy(lastError = if (e.code == 403) "Drive refused access (${e.message})" else "Drive sync failed, will retry") }
            outcome.copy(retry = e.code == 401 || e.code == 429 || e.code >= 500)
        } catch (e: IOException) {
            Log.w(TAG, "Drive sync failed", e)
            save { it.copy(lastError = "No connection to Drive, will retry") }
            outcome.copy(retry = true)
        }
    }

    private fun SyncOutcome.merge(o: SyncOutcome) = copy(uploaded = o.uploaded, restored = o.restored, deleted = o.deleted, retry = retry || o.retry)

    private suspend fun mirror(drive: DriveClient): SyncOutcome = withContext(Dispatchers.IO) {
        val remoteAll = drive.list()
        // Two files for one session (an upload retried after its response was lost): keep the newest.
        val remote = remoteAll.groupBy { it.sessionId }.mapValues { (_, files) ->
            files.sortedByDescending { it.updatedAt }.also { extra -> extra.drop(1).forEach { drive.delete(it.fileId) } }.first()
        }
        val local = sessions.finishedList().associateBy { it.id }
        val synced = _state.value.synced.toMutableMap()
        var uploaded = 0
        var restored = 0
        var deleted = 0

        for (s in local.values) {
            val r = remote[s.id]
            if (r != null && r.updatedAt == s.updatedAt) {
                synced[s.id] = s.updatedAt
                continue
            }
            val full = sessions.full(s.id) ?: continue
            val doc = ExportDocument(exportedAt = System.currentTimeMillis(), appVersion = BuildConfig.VERSION_NAME, sessions = listOf(full.toExport()))
            drive.put(r?.fileId, s.id, s.updatedAt, gzip(ExportJson.encodeToString(ExportDocument.serializer(), doc)))
            synced[s.id] = s.updatedAt
            uploaded++
        }
        for (r in remote.values) {
            if (r.sessionId in local) continue
            val here = sessions.get(r.sessionId)
            when {
                here != null -> Unit // in the bin (Undo still possible) or still recording: leave it
                r.sessionId in synced -> {
                    drive.delete(r.fileId)
                    synced.remove(r.sessionId)
                    deleted++
                }
                else -> {
                    val doc = ExportJson.decodeFromString(ExportDocument.serializer(), gunzip(drive.download(r.fileId)))
                    doc.sessions.forEach { e ->
                        val full = e.toFull().let { it.copy(session = it.session.copy(deletedAt = null)) }
                        if (sessions.import(full)) restored++
                    }
                    synced[r.sessionId] = r.updatedAt
                }
            }
        }
        // Forget sessions gone from both sides.
        synced.keys.retainAll(local.keys + remote.keys)
        save { it.copy(synced = synced, lastSyncAt = System.currentTimeMillis(), lastError = null, journeys = synced.size) }
        if (uploaded + restored + deleted > 0) Log.i(TAG, "Drive: $uploaded uploaded, $restored restored, $deleted deleted")
        SyncOutcome(uploaded = uploaded, restored = restored, deleted = deleted)
    }

    /** Gives unnamed journeys a place name. Stops at the first failed lookup (offline): the worker retries later. */
    private suspend fun nameJourneys(): Int {
        val skip = _state.value.unnameable
        val todo = sessions.finishedList().filter { it.name == null && it.id !in skip && it.startLat != null }
        var named = 0
        namingFailed = false
        for (s in todo) {
            val last = sessions.lastPoint(s.id) ?: continue
            val start = s.startLat!! to s.startLon!!
            val end = last.lat to last.lon
            val name = try {
                PlaceNamer.routeName(namer.place(start.first, start.second), namer.place(end.first, end.second), ActivityType.from(s.activity), start, end)
            } catch (e: IOException) {
                Log.i(TAG, "Geocoder unavailable, naming later: ${e.message}")
                namingFailed = true
                break
            }
            if (name == null) {
                save { it.copy(unnameable = it.unnameable + s.id) }
                continue
            }
            // Only if the user didn't type a name meanwhile.
            sessions.edit(s.id) { cur -> if (cur.name == null) cur.copy(name = name) else cur }
            named++
        }
        if (named > 0) Log.i(TAG, "Named $named journeys")
        return named
    }

    /** Whether a pass has work: a journey to name, or (Drive connected) one to upload or delete there. */
    suspend fun pending(): Boolean {
        val st = _state.value
        val list = sessions.finishedList()
        if (settings.current().autoName && list.any { it.name == null && it.id !in st.unnameable && it.startLat != null }) return true
        if (st.account == null || st.needsReconnect) return false
        if (list.any { st.synced[it.id] != it.updatedAt }) return true
        val ids = list.mapTo(HashSet()) { it.id }
        return st.synced.keys.any { it !in ids && sessions.get(it) == null }
    }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    private fun gunzip(bytes: ByteArray): String = GZIPInputStream(bytes.inputStream()).use { String(it.readBytes()) }

    private companion object {
        const val TAG = "JourneySync"
    }
}
