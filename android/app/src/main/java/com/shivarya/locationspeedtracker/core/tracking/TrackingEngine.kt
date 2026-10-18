package com.shivarya.locationspeedtracker.core.tracking

import android.util.Log
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionState
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.data.toSample
import com.shivarya.locationspeedtracker.core.location.LocationSources
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.units.Units
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What an add-on (sensors, heart rate, steps, auto-pause…) can ask of the running session. */
interface AddonContext {
    val sessionId: String
    val activity: ActivityType
    val settings: AppSettings
    /** Lives as long as the session; cancelled on stop. */
    val scope: CoroutineScope
    fun requestPause(auto: Boolean)
    fun requestResume(auto: Boolean)
}

/**
 * Pluggable per-session recorder. All callbacks run on the engine's single thread, so add-ons need no locking for
 * state touched only from them.
 */
interface SessionAddon {
    fun onStart(ctx: AddonContext) {}
    fun onFix(fix: Fix, accepted: Verdict.Accepted?, live: LiveState) {}
    fun onPause() {}
    fun onResume() {}
    /** Rows to persist; called on every flush. */
    fun drainMetrics(): List<MetricEntity> = emptyList()
    /** Fold the add-on's summary (steps, HR, calories…) into the final session row. */
    fun finish(session: SessionEntity): SessionEntity = session
    fun onStop() {}
    /** An external altitude (barometer) the stats should prefer over GNSS, if available. */
    fun altitudeOverride(): Double? = null
}

/** Runs after a session is saved (e.g. queue the Health Connect sync). */
fun interface SessionFinishedHook {
    suspend fun onFinished(sessionId: String)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AddonBindings {
    @Multibinds abstract fun addons(): Set<SessionAddon>
    @Multibinds abstract fun finishedHooks(): Set<SessionFinishedHook>
}

/**
 * Records sessions. Driven by [TrackingService] (which keeps the process alive and in the foreground); all mutable
 * recording state is confined to a single-threaded dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrackingEngine @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessions: SessionRepository,
    private val sources: LocationSources,
    private val addons: Set<@JvmSuppressWildcards SessionAddon>,
    private val finishedHooks: Set<@JvmSuppressWildcards SessionFinishedHook>,
) {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private var recorder: Recorder? = null
    private var sessionScope: CoroutineScope? = null
    private var locationJob: Job? = null
    private val flushMutex = Mutex()

    private class Recorder(
        val sessionId: String,
        val activity: ActivityType,
        val settings: AppSettings,
        val filter: PointFilter,
        val stats: StatsAccumulator,
        val autoSplits: SplitTracker?,
        val manualLaps: SplitTracker,
        var seq: Int,
        var segment: Int,
        var startLat: Double?,
        var startLon: Double?,
        var hasManualLaps: Boolean,
    ) {
        val pendingPoints = ArrayList<TrackPointEntity>()
        val pendingLaps = ArrayList<LapEntity>()
    }

    // ---- Commands (called by TrackingService / TrackingController) ------------------------------------------------

    suspend fun start(activity: ActivityType): String? = withContext(dispatcher) {
        state.value.active?.let { return@withContext it.sessionId }
        val s = settingsRepository.current()
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        sessions.create(SessionEntity(id = id, startTime = now, activity = activity.stored, state = SessionState.Recording.stored))
        settingsRepository.setLastActivity(activity)
        val r = newRecorder(id, activity, s, seq = 0, segment = 0, stats = SessionStats(), lapIndex = 0, manualIndex = 0, at = now)
        recorder = r
        _state.value = LiveState(
            status = TrackingStatus.Active(id, activity, now, paused = false, pausedMs = 0, pausedAt = null),
            fix = state.value.fix,
        )
        begin(r)
        id
    }

    /** Rebuild a session the service was recording when the process died (sticky restart). */
    suspend fun recover(): Boolean = withContext(dispatcher) {
        if (state.value.active != null) return@withContext true
        val row = sessions.unfinished() ?: return@withContext false
        val s = settingsRepository.current()
        val now = System.currentTimeMillis()
        val pts = sessions.points(row.id)
        val last = pts.lastOrNull()
        val activity = ActivityType.from(row.activity)
        val r = newRecorder(
            row.id, activity, s,
            seq = (last?.seq ?: -1) + 1,
            segment = (last?.segment ?: -1) + 1,
            stats = row.stats,
            lapIndex = sessions.nextLapIndex(row.id, SplitKind.Auto.stored),
            manualIndex = sessions.nextLapIndex(row.id, SplitKind.Manual.stored),
            at = now,
        ).apply {
            startLat = row.startLat
            startLon = row.startLon
        }
        recorder = r
        // The time the process was dead counts as paused, so elapsed continues from what was last persisted.
        val pausedMs = (now - row.startTime - (row.elapsedS * 1000).toLong()).coerceAtLeast(0)
        val wasPaused = row.sessionState == SessionState.Paused
        _state.value = LiveState(
            status = TrackingStatus.Active(row.id, activity, row.startTime, paused = wasPaused, pausedMs = pausedMs, pausedAt = if (wasPaused) now else null),
            stats = row.stats,
            track = ChunkedTrack.of(pts.map { it.toSample() }),
        )
        begin(r)
        Log.i(TAG, "Recovered session ${row.id} (${pts.size} points)")
        true
    }

    suspend fun pause(auto: Boolean = false) = withContext(dispatcher) {
        val a = state.value.active ?: return@withContext
        val r = recorder ?: return@withContext
        if (a.paused) return@withContext
        r.filter.reset()
        r.stats.breakSegment()
        r.segment++
        _state.update { it.copy(status = a.copy(paused = true, pausedAt = System.currentTimeMillis(), autoPaused = auto)) }
        addons.forEach { it.onPause() }
        flush(SessionState.Paused)
    }

    suspend fun resume(auto: Boolean = false) = withContext(dispatcher) {
        val a = state.value.active ?: return@withContext
        if (!a.paused) return@withContext
        // A manual pause is never lifted by auto-resume.
        if (auto && !a.autoPaused) return@withContext
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(status = a.copy(paused = false, pausedMs = a.pausedMs + (now - (a.pausedAt ?: now)), pausedAt = null, autoPaused = false))
        }
        addons.forEach { it.onResume() }
        flush(SessionState.Recording)
    }

    suspend fun lap() = withContext(dispatcher) {
        val r = recorder ?: return@withContext
        val now = System.currentTimeMillis()
        val split = r.manualLaps.manualLap(now, r.stats.stats) ?: return@withContext
        r.hasManualLaps = true
        r.pendingLaps += split.toEntity(r.sessionId)
        _state.update { it.copy(laps = it.laps + split, lastSplit = split) }
    }

    /** Finish the session. Returns its id, or null if nothing was recorded (the empty session is discarded). */
    suspend fun stop(): String? = withContext(dispatcher) {
        val a = state.value.active ?: return@withContext null
        val r = recorder ?: return@withContext null
        // Stop location, the flush loop and add-on collectors before the final writes so none of them can land
        // after (and overwrite) the finished row.
        endSessionScope()
        val now = System.currentTimeMillis()
        val stats = r.stats.stats
        // Close the partial last split / lap so the table adds up to the session.
        r.autoSplits?.manualLap(now, stats)?.takeIf { it.distanceM >= 10 }?.let { r.pendingLaps += it.toEntity(r.sessionId) }
        if (r.hasManualLaps) r.manualLaps.manualLap(now, stats)?.let { r.pendingLaps += it.toEntity(r.sessionId) }
        flushPending(r)
        val elapsedMs = a.elapsedMs(now)
        val pausedMs = now - a.startTime - elapsedMs
        val row = sessions.get(r.sessionId)?.let { current ->
            addons.fold(
                current.copy(
                    state = SessionState.Finished.stored,
                    endTime = now,
                    elapsedS = elapsedMs / 1000.0,
                    pausedS = pausedMs / 1000.0,
                    stats = stats,
                    startLat = r.startLat,
                    startLon = r.startLon,
                ),
            ) { acc, addon -> addon.finish(acc) }
        }
        addons.forEach { it.onStop() }
        recorder = null
        val keep = stats.acceptedPoints > 0
        if (row != null) {
            if (keep) sessions.update(row) else sessions.delete(row.id)
        }
        _state.value = LiveState(fix = state.value.fix, speedMps = state.value.fix?.speed)
        if (keep) finishedHooks.forEach { runCatching { it.onFinished(r.sessionId) } }
        if (keep) r.sessionId else null
    }

    /** The service is going away without a stop (killed / crashed): persist what we have. */
    suspend fun onServiceDestroyed() = withContext(dispatcher) {
        val r = recorder ?: return@withContext
        endSessionScope()
        flushPending(r)
        addons.forEach { it.onStop() }
        recorder = null
        _state.value = LiveState()
    }

    private suspend fun endSessionScope() {
        val job = sessionScope?.coroutineContext?.get(Job)
        sessionScope = null
        locationJob = null
        job?.cancelAndJoin()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---- Recording ------------------------------------------------------------------------------------------------

    private fun newRecorder(
        id: String,
        activity: ActivityType,
        s: AppSettings,
        seq: Int,
        segment: Int,
        stats: SessionStats,
        lapIndex: Int,
        manualIndex: Int,
        at: Long,
    ): Recorder {
        val acc = StatsAccumulator(activity.movingThresholdMps).apply { restore(stats) }
        val split = Units.splitLengthM(s.unitSystem)
        return Recorder(
            sessionId = id,
            activity = activity,
            settings = s,
            filter = PointFilter(FilterConfig.forActivity(activity, s.accuracyThresholdM.toDouble())),
            stats = acc,
            autoSplits = if (s.autoSplits) SplitTracker(SplitKind.Auto, split).apply { start(at, stats, lapIndex) } else null,
            manualLaps = SplitTracker(SplitKind.Manual, 0.0).apply { start(at, stats, manualIndex) },
            seq = seq,
            segment = segment,
            startLat = null,
            startLon = null,
            hasManualLaps = manualIndex > 0,
        )
    }

    private fun begin(r: Recorder) {
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        sessionScope = scope
        val ctx = object : AddonContext {
            override val sessionId = r.sessionId
            override val activity = r.activity
            override val settings = r.settings
            override val scope = scope
            override fun requestPause(auto: Boolean) { scope.launch { pause(auto) } }
            override fun requestResume(auto: Boolean) { scope.launch { resume(auto) } }
        }
        addons.forEach { runCatching { it.onStart(ctx) }.onFailure { e -> Log.w(TAG, "Add-on failed to start", e) } }
        locationJob = scope.launch {
            sources.forMode(r.settings.locationSource).updates(r.settings.intervalMs)
                .catch { e ->
                    Log.w(TAG, "Location updates failed", e)
                    _state.update { it.copy(error = e.message ?: "Location unavailable") }
                }
                .collect { onFix(it) }
        }
        scope.launch {
            while (isActive) {
                delay(FLUSH_EVERY_MS)
                val a = state.value.active ?: break
                flush(if (a.paused) SessionState.Paused else SessionState.Recording)
            }
        }
    }

    private fun onFix(fix: Fix) {
        val r = recorder ?: return
        val live = state.value
        val a = live.active ?: return
        if (a.paused) {
            _state.update { it.copy(fix = fix, speedMps = fix.speed) }
            addons.forEach { it.onFix(fix, null, state.value) }
            return
        }
        when (val v = r.filter.offer(fix)) {
            is Verdict.Rejected -> {
                r.stats.onRejected()
                _state.update { it.copy(fix = fix, speedMps = fix.speed ?: it.speedMps, stats = r.stats.stats) }
                addons.forEach { it.onFix(fix, null, state.value) }
            }
            is Verdict.Accepted -> {
                val baro = addons.firstNotNullOfOrNull { it.altitudeOverride() }
                r.stats.onAccepted(fix, v, baro)
                val stats = r.stats.stats
                if (r.startLat == null) {
                    r.startLat = fix.lat
                    r.startLon = fix.lon
                }
                r.pendingPoints += TrackPointEntity(
                    sessionId = r.sessionId,
                    seq = r.seq++,
                    time = fix.time,
                    lat = fix.lat,
                    lon = fix.lon,
                    altitude = fix.altitude,
                    altitudeMsl = fix.altitudeMsl,
                    speed = v.speedMps,
                    bearing = fix.bearing,
                    hAcc = fix.hAcc,
                    vAcc = fix.vAcc,
                    speedAcc = fix.speedAcc,
                    segment = r.segment,
                    distanceM = stats.distanceM,
                    provider = fix.provider,
                    satellites = fix.satellites,
                )
                val splits = r.autoSplits?.onUpdate(fix.time, stats, v.speedMps).orEmpty()
                r.manualLaps.onUpdate(fix.time, stats, v.speedMps)
                splits.forEach { r.pendingLaps += it.toEntity(r.sessionId) }
                val sample = TrackSample(fix.time, fix.lat, fix.lon, v.speedMps, baro ?: fix.bestAltitude, r.segment, stats.distanceM)
                _state.update {
                    it.copy(
                        fix = fix,
                        speedMps = v.speedMps,
                        stats = stats,
                        track = it.track.append(sample),
                        laps = if (splits.isEmpty()) it.laps else it.laps + splits,
                        lastSplit = splits.lastOrNull() ?: it.lastSplit,
                        currentSplitIndex = it.currentSplitIndex + splits.size,
                        error = null,
                    )
                }
                addons.forEach { it.onFix(fix, v, state.value) }
                // Same single-threaded dispatcher: runs right after this fix, never concurrently with it.
                if (r.pendingPoints.size >= 30) sessionScope?.launch { flushPending(r) }
            }
        }
    }

    private suspend fun flush(state: SessionState) {
        val r = recorder ?: return
        val a = this.state.value.active ?: return
        flushPending(r)
        val row = sessions.get(r.sessionId) ?: return
        val now = System.currentTimeMillis()
        val elapsedMs = a.elapsedMs(now)
        sessions.update(
            row.copy(
                state = state.stored,
                elapsedS = elapsedMs / 1000.0,
                pausedS = (now - a.startTime - elapsedMs) / 1000.0,
                stats = r.stats.stats,
                startLat = r.startLat ?: row.startLat,
                startLon = r.startLon ?: row.startLon,
            ),
        )
    }

    /**
     * Writes buffered rows. Items are removed only after their insert succeeded (a cancelled write keeps them for the
     * next flush); the mutex stops two flushes from removing each other's rows. Fixes keep arriving while we're
     * suspended, so exactly the inserted prefix is dropped.
     */
    private suspend fun flushPending(r: Recorder) = flushMutex.withLock {
        if (r.pendingPoints.isNotEmpty()) {
            val batch = ArrayList(r.pendingPoints)
            sessions.appendPoints(batch)
            r.pendingPoints.subList(0, batch.size).clear()
        }
        if (r.pendingLaps.isNotEmpty()) {
            val batch = ArrayList(r.pendingLaps)
            sessions.appendLaps(batch)
            r.pendingLaps.subList(0, batch.size).clear()
        }
        val metrics = addons.flatMap { it.drainMetrics() }
        if (metrics.isNotEmpty()) sessions.appendMetrics(mergeMetrics(metrics))
    }

    private companion object {
        const val TAG = "TrackingEngine"
        const val FLUSH_EVERY_MS = 5_000L
    }
}

/** Several add-ons may contribute columns to the same (session, second) row; merge them into one. */
internal fun mergeMetrics(rows: List<MetricEntity>): List<MetricEntity> =
    rows.groupBy { it.sessionId to it.time }.map { (_, group) ->
        group.reduce { a, b ->
            a.copy(
                gLong = a.gLong ?: b.gLong,
                gLat = a.gLat ?: b.gLat,
                gPeak = a.gPeak ?: b.gPeak,
                pressureHpa = a.pressureHpa ?: b.pressureHpa,
                baroAltM = a.baroAltM ?: b.baroAltM,
                verticalSpeedMps = a.verticalSpeedMps ?: b.verticalSpeedMps,
                leanDeg = a.leanDeg ?: b.leanDeg,
                heartRate = a.heartRate ?: b.heartRate,
                cadence = a.cadence ?: b.cadence,
                stepsTotal = a.stepsTotal ?: b.stepsTotal,
            )
        }
    }

fun Split.toEntity(sessionId: String) = LapEntity(
    sessionId = sessionId,
    kind = kind.stored,
    lapIndex = index,
    startTime = startTime,
    endTime = endTime,
    distanceM = distanceM,
    movingS = movingS,
    maxSpeedMps = maxSpeedMps,
    elevGainM = elevGainM,
)

fun LapEntity.toSplit() = Split(SplitKind.from(kind), lapIndex, startTime, endTime, distanceM, movingS, maxSpeedMps, elevGainM)
