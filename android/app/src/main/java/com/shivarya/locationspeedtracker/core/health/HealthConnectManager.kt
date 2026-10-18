package com.shivarya.locationspeedtracker.core.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import com.shivarya.locationspeedtracker.core.data.FullSession
import com.shivarya.locationspeedtracker.core.data.displayTitle
import com.shivarya.locationspeedtracker.core.model.ActivityType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthAvailability { Available, NeedsUpdate, NotInstalled }

/**
 * Health Connect: finished workouts are written (exercise session with route, distance, steps, calories, elevation,
 * speed, heart rate) so Google Fit, Samsung Health, Fitbit, Strava etc. can use them; heart rate recorded by *other*
 * apps — e.g. a boAt watch's companion app — is read back for a session's time window.
 */
@Singleton
class HealthConnectManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NeedsUpdate
        else -> HealthAvailability.NotInstalled
    }

    private val client: HealthConnectClient? get() = if (availability() == HealthAvailability.Available) HealthConnectClient.getOrCreate(context) else null

    suspend fun granted(): Set<String> = runCatching { client?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()

    suspend fun canWrite() = granted().contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
    suspend fun canReadHeartRate() = granted().contains(HealthPermission.getReadPermission(HeartRateRecord::class))

    /** Exercise type for an activity, or null when Health Connect has none (drives, motorbike rides). */
    fun exerciseType(a: ActivityType): Int? = when (a) {
        ActivityType.Walk -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        ActivityType.Run -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        ActivityType.Hike -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        ActivityType.Cycle -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        ActivityType.Other -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        ActivityType.Drive, ActivityType.Motorbike -> null
    }

    /**
     * Writes one session. Idempotent: every record carries clientRecordId = "<sessionId>/<kind>", so a retry or a
     * re-sync after editing replaces rather than duplicates (clientRecordVersion = the session's updatedAt).
     */
    suspend fun write(full: FullSession): Boolean {
        val c = client ?: return false
        val s = full.session
        val type = exerciseType(ActivityType.from(s.activity)) ?: return false
        val end = s.endTime ?: return false
        val start = Instant.ofEpochMilli(s.startTime)
        val stop = Instant.ofEpochMilli(end)
        if (!stop.isAfter(start)) return false
        val zone = ZoneId.systemDefault().rules
        val so: ZoneOffset = zone.getOffset(start)
        val eo: ZoneOffset = zone.getOffset(stop)
        val granted = granted()
        val version = s.updatedAt
        fun meta(kind: String) = Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE), "${s.id}/$kind", version)

        val route = if (HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE in granted && full.points.size >= 2) {
            ExerciseRoute(
                full.points.map { p ->
                    ExerciseRoute.Location(
                        time = Instant.ofEpochMilli(p.time),
                        latitude = p.lat,
                        longitude = p.lon,
                        horizontalAccuracy = p.hAcc?.let { Length.meters(it) },
                        verticalAccuracy = p.vAcc?.let { Length.meters(it) },
                        altitude = (p.altitudeMsl ?: p.altitude)?.let { Length.meters(it) },
                    )
                // Health Connect's session window is end-exclusive: a point exactly at the end is "out of range".
                }.filter { !it.time.isBefore(start) && it.time.isBefore(stop) },
            )
        } else null

        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start, startZoneOffset = so, endTime = stop, endZoneOffset = eo,
                metadata = meta("session"),
                exerciseType = type,
                title = s.displayTitle(),
                notes = s.notes,
                exerciseRoute = route,
            ),
        )
        if (s.stats.distanceM > 0) {
            records += DistanceRecord(start, so, stop, eo, Length.meters(s.stats.distanceM), meta("distance"))
        }
        s.steps?.takeIf { it > 0 }?.let { records += StepsRecord(start, so, stop, eo, it.toLong(), meta("steps")) }
        s.calories?.takeIf { it > 0 }?.let { records += TotalCaloriesBurnedRecord(start, so, stop, eo, Energy.kilocalories(it), meta("calories")) }
        if (s.stats.elevGainM > 0) records += ElevationGainedRecord(start, so, stop, eo, Length.meters(s.stats.elevGainM), meta("elevation"))
        // Speed: one sample per ~10 s keeps the record small; HC caps samples per record.
        val speedSamples = full.points.filterIndexed { i, _ -> i % 10 == 0 }.mapNotNull { p ->
            p.speed?.let { SpeedRecord.Sample(Instant.ofEpochMilli(p.time), Velocity.metersPerSecond(it)) }
        }.filter { !it.time.isBefore(start) && it.time.isBefore(stop) }
        if (speedSamples.isNotEmpty()) records += SpeedRecord(start, so, stop, eo, speedSamples.take(10_000), meta("speed"))
        val hr = full.metrics.mapNotNull { m -> m.heartRate?.let { HeartRateRecord.Sample(Instant.ofEpochMilli(m.time), it.toLong()) } }
            .filter { !it.time.isBefore(start) && it.time.isBefore(stop) }
        // Heart rate that came *from* Health Connect (a watch's app) must not be written back as ours.
        if (hr.isNotEmpty() && s.hrSource != HR_SOURCE_HEALTH) records += HeartRateRecord(start, so, stop, eo, hr.take(10_000), meta("heartrate"))

        val writable = records.filter { HealthPermission.getWritePermission(it::class) in granted }
        if (writable.none { it is ExerciseSessionRecord }) return false
        return runCatching { c.insertRecords(writable); true }
            .onFailure { Log.w(TAG, "Health Connect write failed", it) }
            .getOrDefault(false)
    }

    /** Heart rate written by other apps (watch companions) between [startMs] and [endMs]: (epoch ms, bpm). */
    suspend fun readHeartRate(startMs: Long, endMs: Long): List<Pair<Long, Int>> {
        val c = client ?: return emptyList()
        if (endMs <= startMs || !canReadHeartRate()) return emptyList()
        return runCatching {
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs)),
                ),
            )
            response.records
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .flatMap { r -> r.samples.map { it.time.toEpochMilli() to it.beatsPerMinute.toInt() } }
                .filter { (t, bpm) -> t in startMs..endMs && bpm in 25..250 }
                .sortedBy { it.first }
        }.onFailure { Log.w(TAG, "Health Connect read failed", it) }.getOrDefault(emptyList())
    }

    companion object {
        private const val TAG = "HealthConnect"
        const val HR_SOURCE_BLE = "ble"
        const val HR_SOURCE_HEALTH = "health_connect"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
