package com.shivarya.locationspeedtracker.core.data

import androidx.room.withTransaction
import com.shivarya.locationspeedtracker.core.data.db.AppDatabase
import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionState
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** A session plus everything recorded with it — the unit of import, export and backup. */
data class FullSession(
    val session: SessionEntity,
    val points: List<TrackPointEntity>,
    val laps: List<LapEntity>,
    val metrics: List<MetricEntity>,
)

@Singleton
class SessionRepository @Inject constructor(private val db: AppDatabase) {
    private val sessions = db.sessions()
    private val points = db.points()
    private val laps = db.laps()
    private val metrics = db.metrics()

    val finished: Flow<List<SessionEntity>> = sessions.observeFinished()
    val finishedCount: Flow<Int> = sessions.observeCount()

    suspend fun finishedList() = sessions.finished()
    fun observe(id: String): Flow<SessionEntity?> = sessions.observe(id)
    suspend fun get(id: String): SessionEntity? = sessions.get(id)
    suspend fun points(id: String): List<TrackPointEntity> = points.forSession(id)
    fun observePoints(id: String) = points.observe(id)
    suspend fun lastPoint(id: String) = points.last(id)
    suspend fun laps(id: String): List<LapEntity> = laps.forSession(id)
    fun observeLaps(id: String) = laps.observe(id)
    suspend fun metrics(id: String): List<MetricEntity> = metrics.forSession(id)
    suspend fun nextLapIndex(id: String, kind: String) = laps.nextIndex(id, kind)
    suspend fun unfinished(): SessionEntity? = sessions.unfinished()
    suspend fun legacyCount() = sessions.legacyCount()

    suspend fun full(id: String): FullSession? {
        val s = sessions.get(id) ?: return null
        return FullSession(s, points.forSession(id), laps.forSession(id), metrics.forSession(id))
    }

    suspend fun create(session: SessionEntity) = sessions.insert(session)

    suspend fun update(session: SessionEntity) = sessions.update(session.copy(updatedAt = System.currentTimeMillis()))

    suspend fun appendPoints(rows: List<TrackPointEntity>) {
        if (rows.isNotEmpty()) points.insertAll(rows)
    }

    suspend fun appendLaps(rows: List<LapEntity>) {
        if (rows.isNotEmpty()) laps.insertAll(rows)
    }

    suspend fun appendMetrics(rows: List<MetricEntity>) {
        if (rows.isNotEmpty()) metrics.insertAll(rows)
    }

    /**
     * Adds heart-rate samples from elsewhere (Health Connect) to a session: one per second, filling rows that have
     * none and creating rows where the session recorded no sensor data. Updates the session's averages.
     */
    suspend fun backfillHeartRate(id: String, samples: List<Pair<Long, Int>>, source: String): Boolean = db.withTransaction {
        val s = sessions.get(id) ?: return@withTransaction false
        val perSecond = samples.groupBy { it.first / 1000 * 1000 }.mapValues { (_, v) -> v.last().second }
        if (perSecond.isEmpty()) return@withTransaction false
        metrics.insertIgnore(perSecond.map { (t, bpm) -> MetricEntity(sessionId = id, time = t, heartRate = bpm) })
        perSecond.forEach { (t, bpm) -> metrics.fillHeartRate(id, t, bpm) }
        val bpms = perSecond.values
        sessions.update(s.copy(avgHr = bpms.average().toInt(), maxHr = bpms.max(), hrSource = source, updatedAt = System.currentTimeMillis()))
        true
    }

    suspend fun edit(id: String, transform: (SessionEntity) -> SessionEntity) {
        val s = sessions.get(id) ?: return
        update(transform(s))
    }

    suspend fun rename(id: String, name: String?) = edit(id) { it.copy(name = name?.trim()?.takeIf(String::isNotEmpty)) }
    suspend fun setNotes(id: String, notes: String?) = edit(id) { it.copy(notes = notes?.trim()?.takeIf(String::isNotEmpty)) }
    suspend fun setActivity(id: String, activity: ActivityType) = edit(id) { it.copy(activity = activity.stored) }

    suspend fun softDelete(ids: List<String>) = sessions.softDelete(ids, System.currentTimeMillis())
    suspend fun restore(ids: List<String>) = sessions.restore(ids)
    suspend fun purgeDeleted() = sessions.purgeDeleted()
    suspend fun deleteAll() = sessions.deleteAll()

    suspend fun delete(id: String) = db.withTransaction {
        sessions.softDelete(listOf(id), System.currentTimeMillis())
        sessions.purgeDeleted()
    }

    /** Inserts a complete session unless one with the same id exists. Returns true if it was new. */
    suspend fun import(full: FullSession): Boolean = db.withTransaction {
        if (sessions.insertIgnore(full.session) == -1L) return@withTransaction false
        points.insertAll(full.points)
        if (full.laps.isNotEmpty()) laps.insertAll(full.laps.map { it.copy(id = 0) })
        if (full.metrics.isNotEmpty()) metrics.insertAll(full.metrics)
        true
    }

    /** Marks a session the recorder lost (process died, service never restarted) as finished. */
    suspend fun finalizeOrphan(s: SessionEntity) {
        val last = points.last(s.id)
        val end = last?.time ?: s.updatedAt
        if (last == null) {
            delete(s.id)
            return
        }
        update(s.copy(state = SessionState.Finished.stored, endTime = end))
    }
}

fun TrackPointEntity.toSample() = TrackSample(
    time = time,
    lat = lat,
    lon = lon,
    speedMps = speed ?: 0.0,
    altitude = altitudeMsl ?: altitude,
    segment = segment,
    distanceM = distanceM,
)
