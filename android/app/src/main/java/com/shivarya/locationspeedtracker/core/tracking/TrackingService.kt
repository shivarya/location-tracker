package com.shivarya.locationspeedtracker.core.tracking

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.shivarya.locationspeedtracker.core.ApplicationScope
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.widget.QuickAccess
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Location foreground service: keeps recording with the screen off or the app in the background. It must be
 * started while the app is visible (or from a notification / widget tap) — Android 14+ only grants while-in-use
 * location to services started that way, which is why the app needs no background-location permission.
 */
@AndroidEntryPoint
class TrackingService : LifecycleService() {

    @Inject lateinit var engine: TrackingEngine
    @Inject lateinit var notifier: TrackingNotifier
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private var units = UnitSystem.Metric
    private var notificationJob: Job? = null
    private var foregroundTypes = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                if (!goForeground()) return START_NOT_STICKY
                val activity = ActivityType.from(intent.getStringExtra(EXTRA_ACTIVITY))
                appScope.launch {
                    runCatching { engine.start(activity) }.onFailure {
                        Log.e(TAG, "Could not start a session", it)
                        stopSelfCleanly()
                    }
                }
                observe()
            }
            ACTION_PAUSE -> appScope.launch { engine.pause() }
            ACTION_RESUME -> appScope.launch { engine.resume() }
            ACTION_LAP -> appScope.launch { engine.lap() }
            ACTION_STOP -> appScope.launch {
                engine.stop()
                stopSelfCleanly()
            }
            ACTION_UPDATE_TYPES -> if (engine.state.value.active != null) goForeground()
            null -> {
                // Sticky restart after the process was killed mid-session.
                if (!goForeground()) {
                    notifier.postResumePrompt()
                    stopSelf()
                    return START_NOT_STICKY
                }
                appScope.launch {
                    if (engine.recover()) observe() else stopSelfCleanly()
                }
            }
        }
        return START_STICKY
    }

    /** Foreground with the location type (plus connectedDevice while a BLE sensor is in use). */
    @SuppressLint("InlinedApi") // ServiceCompat drops the type argument below API 29.
    private fun goForeground(): Boolean {
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or extraForegroundTypes(this)
        return try {
            ServiceCompat.startForeground(
                this,
                TrackingNotifier.NOTIFICATION_ID,
                notifier.build(engine.state.value, units),
                types,
            )
            foregroundTypes = types
            notifier.cancelResumePrompt()
            true
        } catch (e: Exception) {
            val notAllowed = Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException
            Log.w(TAG, if (notAllowed) "Foreground start not allowed from background" else "startForeground failed", e)
            false
        }
    }

    private fun observe() {
        if (notificationJob != null) return
        notificationJob = lifecycleScope.launch {
            launch { settings.settings.collectLatest { units = it.unitSystem } }
            launch {
                // Refresh at most every 2 s; also on every pause/resume/stop transition.
                var started = false
                engine.state.distinctUntilChangedBy { it.status }.collectLatest { live ->
                    if (live.active == null) {
                        if (started) {
                            QuickAccess.refresh(this@TrackingService)
                            stopSelfCleanly()
                        }
                        return@collectLatest
                    }
                    started = true
                    var tick = 0
                    while (true) {
                        notifier.update(notifier.build(engine.state.value, units))
                        // Widget / tile every ~6 s: Glance updates are comparatively expensive.
                        if (tick++ % 3 == 0) QuickAccess.refresh(this@TrackingService)
                        delay(2_000)
                    }
                }
            }
        }
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        appScope.launch { engine.onServiceDestroyed() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TrackingService"
        const val ACTION_START = "com.shivarya.locationspeedtracker.START"
        const val ACTION_PAUSE = "com.shivarya.locationspeedtracker.PAUSE"
        const val ACTION_RESUME = "com.shivarya.locationspeedtracker.RESUME"
        const val ACTION_LAP = "com.shivarya.locationspeedtracker.LAP"
        const val ACTION_STOP = "com.shivarya.locationspeedtracker.STOP"
        /** Re-run startForeground so a newly connected BLE sensor adds the connectedDevice type. */
        const val ACTION_UPDATE_TYPES = "com.shivarya.locationspeedtracker.UPDATE_TYPES"
        const val EXTRA_ACTIVITY = "activity"

        /** Extra foreground-service types contributed by optional features (set by the heart-rate add-on). */
        @Volatile var extraForegroundTypes: (Context) -> Int = { 0 }
    }
}

/** Starts/controls recording from UI, widget and tile. */
@Singleton
class TrackingController @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val engine: TrackingEngine,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    val state get() = engine.state

    /** Must be called while the app is visible (or from a widget / notification interaction). */
    fun start(activity: ActivityType) {
        val intent = Intent(context, TrackingService::class.java)
            .setAction(TrackingService.ACTION_START)
            .putExtra(TrackingService.EXTRA_ACTIVITY, activity.stored)
        ContextCompat.startForegroundService(context, intent)
    }

    fun clearError() = engine.clearError()

    fun pause() { scope.launch { engine.pause() } }
    fun resume() { scope.launch { engine.resume() } }
    fun lap() { scope.launch { engine.lap() } }

    /** Stops recording; suspends until the session row is final. Returns the saved session id, if any. */
    suspend fun stop(): String? = engine.stop()

    fun refreshForegroundTypes() {
        if (engine.state.value.active == null) return
        runCatching {
            context.startService(Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_UPDATE_TYPES))
        }
    }
}
