package com.shivarya.locationspeedtracker.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shivarya.locationspeedtracker.core.tracking.SessionStats
import kotlinx.serialization.Serializable

enum class SessionState(val stored: String) {
    Recording("recording"), Paused("paused"), Finished("finished");

    companion object {
        fun from(s: String) = entries.firstOrNull { it.stored == s } ?: Finished
    }
}

enum class SessionSource(val stored: String) {
    Native("native"),
    /** Imported from the Expo app's AsyncStorage on first launch. */
    Legacy("legacy"),
    /** GPX import / backup restore. */
    Import("import");

    companion object {
        fun from(s: String) = entries.firstOrNull { it.stored == s } ?: Native
    }
}

@Entity(tableName = "sessions", indices = [Index("startTime")])
@Serializable
data class SessionEntity(
    @PrimaryKey val id: String,
    val startTime: Long,
    val endTime: Long? = null,
    val activity: String,
    val name: String? = null,
    val notes: String? = null,
    val source: String = SessionSource.Native.stored,
    val state: String = SessionState.Recording.stored,
    /** Active time: wall clock from start to end minus pauses. */
    val elapsedS: Double = 0.0,
    val pausedS: Double = 0.0,
    @Embedded val stats: SessionStats = SessionStats(),
    val steps: Int? = null,
    val avgCadence: Double? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    /** Where the heart rate came from: "ble" (paired sensor) or "health_connect" (read back from a watch app). */
    val hrSource: String? = null,
    val calories: Double? = null,
    val maxG: Double? = null,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val healthConnectSyncedAt: Long? = null,
    /** Soft delete so History can offer Undo; purged on the next launch. */
    val deletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val sessionState get() = SessionState.from(state)
    val sessionSource get() = SessionSource.from(source)
}

@Entity(
    tableName = "track_points",
    primaryKeys = ["sessionId", "seq"],
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
)
@Serializable
data class TrackPointEntity(
    val sessionId: String,
    val seq: Int,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val altitudeMsl: Double? = null,
    /** The filter's speed estimate (m/s) — what the stats used. */
    val speed: Double? = null,
    val bearing: Double? = null,
    val hAcc: Double? = null,
    val vAcc: Double? = null,
    val speedAcc: Double? = null,
    /** Increments after every pause; the route is not joined across segments. */
    val segment: Int = 0,
    /** Cumulative session distance at this point (m) — charts by distance, split lookups. */
    val distanceM: Double = 0.0,
    val provider: String? = null,
    val satellites: Int? = null,
)

@Entity(
    tableName = "laps",
    indices = [Index("sessionId")],
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
)
@Serializable
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    /** "auto" (every km/mi) or "manual" (lap button). */
    val kind: String,
    val lapIndex: Int,
    val startTime: Long,
    val endTime: Long,
    val distanceM: Double,
    val movingS: Double,
    val maxSpeedMps: Double,
    val elevGainM: Double,
    val avgHr: Int? = null,
)

/** One row per second of sensor data recorded alongside the GPS track. */
@Entity(
    tableName = "metrics",
    primaryKeys = ["sessionId", "time"],
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
)
@Serializable
data class MetricEntity(
    val sessionId: String,
    val time: Long,
    /** Longitudinal / lateral acceleration in g (vehicle frame), and the peak |a| within the second. */
    val gLong: Float? = null,
    val gLat: Float? = null,
    val gPeak: Float? = null,
    val pressureHpa: Float? = null,
    val baroAltM: Float? = null,
    val verticalSpeedMps: Float? = null,
    val leanDeg: Float? = null,
    val heartRate: Int? = null,
    val cadence: Float? = null,
    val stepsTotal: Int? = null,
)

@Entity(tableName = "perf_runs", indices = [Index("time")])
@Serializable
data class PerfRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    /** PerfMode.stored */
    val mode: String,
    val label: String,
    /** Elapsed seconds for the run (time to target, or braking time). */
    val resultS: Double,
    val distanceM: Double,
    val startSpeedMps: Double,
    val endSpeedMps: Double,
    val maxSpeedMps: Double,
    val peakG: Double? = null,
    val rolloutApplied: Boolean = false,
    /** JSON array of [tSeconds, speedMps] samples for the result graph. */
    val traceJson: String = "[]",
    val notes: String? = null,
)
