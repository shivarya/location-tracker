package com.shivarya.locationspeedtracker.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** One [JourneySync] pass, repeated while edits made during it (or by it) leave work behind. Network required. */
@HiltWorker
class JourneySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: JourneySync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        var passes = 0
        var outcome: SyncOutcome
        do {
            outcome = sync.run()
            passes++
        } while (!outcome.retry && passes < MAX_PASSES && sync.pending())
        if (outcome.retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Journey sync crashed", e)
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "JourneySync"
        private const val MAX_PASSES = 3
        private const val MAX_ATTEMPTS = 5
        private const val NOW = "journey-sync"
        private const val PERIODIC = "journey-sync-periodic"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Soon, once online. A run already queued or running stays (it re-checks for leftover work at the end). */
        fun request(context: Context, delaySeconds: Long = 10) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<JourneySyncWorker>()
                    .setConstraints(online)
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build(),
            )
        }

        /** Catch-up twice a day for anything a one-off run missed (offline, process killed). */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<JourneySyncWorker>(12, TimeUnit.HOURS).setConstraints(online).build(),
            )
        }
    }
}

/** Started with the app: asks for a sync whenever journeys change, naming is switched on, or Drive is connected. */
@Singleton
class JourneySyncTrigger @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val sync: JourneySync,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        combine(
            sessions.finished.map { list -> list.map { it.id to it.updatedAt } }.distinctUntilChanged(),
            settings.settings.map { it.autoName }.distinctUntilChanged(),
            sync.state.map { it.account to it.needsReconnect }.distinctUntilChanged(),
        ) { _, _, _ -> }
            .debounce(2_000)
            .onEach {
                if (sync.pending()) JourneySyncWorker.request(context)
                if (sync.state.value.account != null || settings.current().autoName) JourneySyncWorker.schedulePeriodic(context)
            }
            .launchIn(scope)
    }

    /** "Sync now": right away, even if nothing looks pending (e.g. to restore from another phone). */
    fun now() {
        scope.launch { JourneySyncWorker.request(context, delaySeconds = 0) }
    }
}
