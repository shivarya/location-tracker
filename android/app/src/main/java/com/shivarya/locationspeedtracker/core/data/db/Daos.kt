package com.shivarya.locationspeedtracker.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL AND state = 'finished' ORDER BY startTime DESC")
    fun observeFinished(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL AND state = 'finished' ORDER BY startTime DESC")
    suspend fun finished(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<SessionEntity>

    /** A session left recording/paused — the service died mid-session. */
    @Query("SELECT * FROM sessions WHERE state != 'finished' AND deletedAt IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun unfinished(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    /** Re-imports (legacy migration retried, backup restored twice) must not duplicate sessions. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("UPDATE sessions SET deletedAt = :at WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, at: Long)

    @Query("UPDATE sessions SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>)

    @Query("DELETE FROM sessions WHERE deletedAt IS NOT NULL")
    suspend fun purgeDeleted()

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sessions WHERE deletedAt IS NULL AND state = 'finished'")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sessions WHERE source = 'legacy'")
    suspend fun legacyCount(): Int
}

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY seq")
    suspend fun forSession(sessionId: String): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY seq")
    fun observe(sessionId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY seq DESC LIMIT 1")
    suspend fun last(sessionId: String): TrackPointEntity?

    @Query("SELECT COUNT(*) FROM track_points WHERE sessionId = :sessionId")
    suspend fun count(sessionId: String): Int
}

@Dao
interface LapDao {
    @Insert
    suspend fun insertAll(laps: List<LapEntity>)

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY kind, lapIndex")
    suspend fun forSession(sessionId: String): List<LapEntity>

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY kind, lapIndex")
    fun observe(sessionId: String): Flow<List<LapEntity>>

    @Query("SELECT COALESCE(MAX(lapIndex) + 1, 0) FROM laps WHERE sessionId = :sessionId AND kind = :kind")
    suspend fun nextIndex(sessionId: String, kind: String): Int
}

@Dao
interface MetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<MetricEntity>)

    @Query("SELECT * FROM metrics WHERE sessionId = :sessionId ORDER BY time")
    suspend fun forSession(sessionId: String): List<MetricEntity>

    @Query("UPDATE metrics SET heartRate = :bpm WHERE sessionId = :sessionId AND time = :time AND heartRate IS NULL")
    suspend fun fillHeartRate(sessionId: String, time: Long, bpm: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(rows: List<MetricEntity>)
}

@Dao
interface PerfRunDao {
    @Insert
    suspend fun insert(run: PerfRunEntity): Long

    @Query("SELECT * FROM perf_runs ORDER BY time DESC")
    fun observeAll(): Flow<List<PerfRunEntity>>

    @Query("DELETE FROM perf_runs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM perf_runs ORDER BY time DESC")
    suspend fun all(): List<PerfRunEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(runs: List<PerfRunEntity>)
}
