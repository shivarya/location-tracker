package com.shivarya.locationspeedtracker.core.health

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.SessionFinishedHook
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/**
 * Per session: first pull heart rate a watch app wrote to Health Connect (if the session has none of its own), then
 * write the workout. A second, delayed run repeats the heart-rate check, because watch apps often sync hours later.
 */
@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val health: HealthConnectManager,
    private val sessions: SessionRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val writeToo = inputData.getBoolean(KEY_WRITE, true)
        if (health.availability() != HealthAvailability.Available) return Result.success()
        return try {
            val s = sessions.get(id) ?: return Result.success()
            val end = s.endTime ?: return Result.success()
            if (s.avgHr == null || s.hrSource == HealthConnectManager.HR_SOURCE_HEALTH) {
                val hr = health.readHeartRate(s.startTime, end)
                if (hr.isNotEmpty()) sessions.backfillHeartRate(id, hr, HealthConnectManager.HR_SOURCE_HEALTH)
            }
            if (writeToo && health.exerciseType(ActivityType.from(s.activity)) != null && health.canWrite()) {
                val full = sessions.full(id) ?: return Result.success()
                if (health.write(full)) {
                    sessions.edit(id) { it.copy(healthConnectSyncedAt = System.currentTimeMillis()) }
                    Log.i(TAG, "Wrote session $id to Health Connect (${full.points.size} route points)")
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e // replaced by a newer request for the same session, or stopped by WorkManager
        } catch (e: Exception) {
            Log.w(TAG, "Sync of $id failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "HealthSync"
        private const val KEY_ID = "session"
        private const val KEY_WRITE = "write"

        fun enqueue(context: Context, sessionId: String) {
            val wm = WorkManager.getInstance(context)
            val now = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInputData(workDataOf(KEY_ID to sessionId, KEY_WRITE to true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            wm.enqueueUniqueWork("hc-$sessionId", ExistingWorkPolicy.REPLACE, now)
            val later = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInputData(workDataOf(KEY_ID to sessionId, KEY_WRITE to false))
                .setInitialDelay(3, TimeUnit.HOURS)
                .build()
            wm.enqueueUniqueWork("hc-hr-$sessionId", ExistingWorkPolicy.REPLACE, later)
        }

        /** Manual "fetch heart rate" — read only, right now. */
        fun fetchHeartRate(context: Context, sessionId: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "hc-hr-now-$sessionId",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HealthSyncWorker>().setInputData(workDataOf(KEY_ID to sessionId, KEY_WRITE to false)).build(),
            )
        }
    }
}

class HealthSyncHook @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : SessionFinishedHook {
    override suspend fun onFinished(sessionId: String) {
        if (settings.current().healthConnectSync) HealthSyncWorker.enqueue(context, sessionId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds @IntoSet abstract fun hook(h: HealthSyncHook): SessionFinishedHook
}
