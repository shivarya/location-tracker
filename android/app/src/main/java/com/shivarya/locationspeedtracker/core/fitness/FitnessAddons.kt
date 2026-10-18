package com.shivarya.locationspeedtracker.core.fitness

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.health.HealthConnectManager
import com.shivarya.locationspeedtracker.core.hr.HeartRateMonitor
import com.shivarya.locationspeedtracker.core.hr.HrConnection
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.Profile
import com.shivarya.locationspeedtracker.core.tracking.AddonContext
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.LiveState
import com.shivarya.locationspeedtracker.core.tracking.SessionAddon
import com.shivarya.locationspeedtracker.core.tracking.TrackingService
import com.shivarya.locationspeedtracker.core.tracking.Verdict
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class FitnessLive(val calories: Double = 0.0)

/**
 * Calories (live and per session) and auto-pause. Auto-pause pauses after the activity's "stopped" speed has held
 * for a few seconds and resumes on two consecutive moving fixes; a manual pause is never auto-resumed.
 */
@Singleton
class FitnessAddon @Inject constructor(private val hr: HeartRateMonitor) : SessionAddon {
    private val _live = MutableStateFlow<FitnessLive?>(null)
    val live: StateFlow<FitnessLive?> = _live.asStateFlow()

    private var ctx: AddonContext? = null
    private lateinit var activity: ActivityType
    private var profile = Profile()
    private var age = 35
    private var kcal = 0.0
    private var autoPause = false
    private var stillSince: Long? = null
    private var movingFixes = 0
    private val window = ArrayDeque<Pair<Double, Double>>() // (distance m, altitude m)

    override fun onStart(ctx: AddonContext) {
        this.ctx = ctx
        activity = ctx.activity
        profile = ctx.settings.profile
        age = (Calendar.getInstance().get(Calendar.YEAR) - profile.birthYear).coerceIn(10, 100)
        autoPause = ctx.settings.autoPause
        kcal = 0.0
        stillSince = null
        movingFixes = 0
        window.clear()
        _live.value = if (activity.motorized) null else FitnessLive()
    }

    override fun onFix(fix: Fix, accepted: Verdict.Accepted?, live: LiveState) {
        val c = ctx ?: return
        val a = live.active ?: return
        val speed = accepted?.speedMps ?: fix.speed ?: 0.0
        if (autoPause) {
            val threshold = activity.movingThresholdMps
            if (!a.paused) {
                if (speed < threshold) {
                    val since = stillSince ?: fix.time.also { stillSince = it }
                    if (fix.time - since >= pauseDelayMs()) {
                        stillSince = null
                        c.requestPause(auto = true)
                    }
                } else stillSince = null
            } else if (a.autoPaused) {
                // Doppler speed with a sane accuracy: positions alone jitter enough to fake movement.
                val credible = (fix.hAcc ?: 99.0) <= 30 && (fix.speed ?: 0.0) > threshold * 1.6
                movingFixes = if (credible) movingFixes + 1 else 0
                if (movingFixes >= 2) {
                    movingFixes = 0
                    c.requestResume(auto = true)
                }
            }
        }
        if (accepted != null && accepted.dtS > 0 && accepted.moving && !activity.motorized) {
            val alt = fix.bestAltitude
            if (alt != null) {
                window.addLast(live.stats.distanceM to alt)
                while (window.size > 2 && live.stats.distanceM - window.first().first > 60) window.removeFirst()
            }
            val grade = if (window.size >= 2 && window.last().first - window.first().first > 20) {
                (window.last().second - window.first().second) / (window.last().first - window.first().first)
            } else 0.0
            val bpm = hr.state.value.liveBpm()
            kcal += CalorieCalculator.kcalPerMinute(activity, speed, grade, bpm, profile, age) * accepted.dtS.coerceAtMost(30.0) / 60.0
            _live.value = FitnessLive(kcal)
        }
    }

    private fun pauseDelayMs() = if (activity.onFoot || activity == ActivityType.Cycle) 6_000L else 10_000L

    override fun finish(session: SessionEntity): SessionEntity =
        if (activity.motorized || kcal <= 0.5) session else session.copy(calories = kcal)

    override fun onStop() {
        ctx = null
        _live.value = null
    }
}

/**
 * Live heart rate from the paired BLE sensor, recorded once per second. While the sensor is connected the
 * foreground service also declares the connectedDevice type (Android 14+ requires it for BLE work in the background).
 */
@Singleton
class HeartRateAddon @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val monitor: HeartRateMonitor,
) : SessionAddon {
    private val lock = Any()
    private var sessionId = ""
    private var active = false
    private var paused = false
    private var lastSecond = -1L
    private val pending = ArrayList<MetricEntity>()
    private var sum = 0L
    private var n = 0
    private var maxBpm = 0

    @SuppressLint("InlinedApi") // Only passed to ServiceCompat.startForeground, which ignores types below API 29.
    override fun onStart(ctx: AddonContext) {
        val address = ctx.settings.hrDeviceAddress ?: return
        if (!monitor.hasPermissions()) return
        synchronized(lock) {
            sessionId = ctx.sessionId
            active = true
            paused = false
            lastSecond = -1
            pending.clear()
            sum = 0
            n = 0
            maxBpm = 0
        }
        monitor.acquire(address, ctx.settings.hrDeviceName)
        TrackingService.extraForegroundTypes = {
            if (monitor.state.value.connection == HrConnection.Connected && monitor.hasPermissions()) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        }
        ctx.scope.launch {
            monitor.state.map { it.connection == HrConnection.Connected }.distinctUntilChanged().collect {
                runCatching {
                    context.startService(Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_UPDATE_TYPES))
                }
            }
        }
        ctx.scope.launch {
            monitor.state.collect { s ->
                val bpm = s.liveBpm() ?: return@collect
                synchronized(lock) {
                    if (paused) return@synchronized
                    val second = s.updatedAt / 1000
                    if (second == lastSecond) return@synchronized
                    lastSecond = second
                    pending += MetricEntity(sessionId = sessionId, time = second * 1000, heartRate = bpm)
                    sum += bpm
                    n++
                    maxBpm = max(maxBpm, bpm)
                }
            }
        }
    }

    override fun onPause() = synchronized(lock) { paused = true }
    override fun onResume() = synchronized(lock) { paused = false }

    override fun drainMetrics(): List<MetricEntity> = synchronized(lock) {
        if (pending.isEmpty()) emptyList() else ArrayList(pending).also { pending.clear() }
    }

    override fun finish(session: SessionEntity): SessionEntity = synchronized(lock) {
        if (n == 0) session else session.copy(avgHr = (sum / n).toInt(), maxHr = maxBpm, hrSource = HealthConnectManager.HR_SOURCE_BLE)
    }

    override fun onStop() {
        val wasActive = synchronized(lock) { active.also { active = false } }
        if (wasActive) monitor.release()
        TrackingService.extraForegroundTypes = { 0 }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FitnessAddonModule {
    @Binds @IntoSet abstract fun fitness(a: FitnessAddon): SessionAddon
    @Binds @IntoSet abstract fun heartRate(a: HeartRateAddon): SessionAddon
}
