package com.shivarya.locationspeedtracker.feature.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.location.LocationPermissions
import com.shivarya.locationspeedtracker.core.location.LocationSources
import com.shivarya.locationspeedtracker.core.location.NetworkMonitor
import com.shivarya.locationspeedtracker.core.activity.ActivityRecognizer
import com.shivarya.locationspeedtracker.core.activity.DetectedMotion
import com.shivarya.locationspeedtracker.core.fitness.FitnessAddon
import com.shivarya.locationspeedtracker.core.fitness.FitnessLive
import com.shivarya.locationspeedtracker.core.hr.HeartRateMonitor
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.sensors.SensorLive
import com.shivarya.locationspeedtracker.core.sensors.SensorRecorder
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.LiveState
import com.shivarya.locationspeedtracker.core.tracking.TrackingController
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackUi(
    val live: LiveState = LiveState(),
    /** Fix to display: the recorder's while a session runs, otherwise the screen's own preview stream. */
    val fix: Fix? = null,
    val speedMps: Double? = null,
    val now: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val online: Boolean = true,
    val activity: ActivityType = ActivityType.Other,
    val sensors: SensorLive? = null,
    val heartRate: Int? = null,
    val calories: Double? = null,
    /** Activity Recognition's guess, when it differs from the selected type (idle only). */
    val suggestion: ActivityType? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackViewModel @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
    private val controller: TrackingController,
    private val settingsRepository: SettingsRepository,
    private val sessions: SessionRepository,
    sources: LocationSources,
    network: NetworkMonitor,
    sensorRecorder: SensorRecorder,
    fitness: FitnessAddon,
    hr: HeartRateMonitor,
    private val recognizer: ActivityRecognizer,
) : ViewModel() {

    private val previewFix = MutableStateFlow<Fix?>(null)
    private val visible = MutableStateFlow(false)
    private val permissionVersion = MutableStateFlow(0)
    private val selected = MutableStateFlow<ActivityType?>(null)
    private val events = Channel<TrackEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    val ui: StateFlow<TrackUi> = combine(
        controller.state,
        combine(settings, selected) { s, sel -> s to (sel ?: s.lastActivity) },
        combine(previewFix, sensorRecorder.live, fitness.live, hr.state, recognizer.detected) { p, sl, fl, h, d -> Extras(p, sl, fl, h.liveBpm(), d) },
        network.online,
        ticker,
    ) { live, (s, sel), x, online, now ->
        val preview = x.preview
        val sensorLive = x.sensors
        val a = live.active
        val fix = if (a != null) live.fix ?: preview else preview ?: live.fix
        TrackUi(
            live = live,
            fix = fix,
            speedMps = if (a != null) live.speedMps else fix?.speed,
            now = now,
            settings = s,
            online = online,
            activity = a?.activity ?: sel,
            sensors = sensorLive.takeIf { a != null },
            heartRate = x.bpm,
            calories = x.fitness?.calories?.takeIf { a != null },
            suggestion = x.detected?.type?.takeIf { a == null && it != sel && now - x.detected.at < 10 * 60_000 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackUi())

    init {
        // While idle and on screen, show live speed/position without recording (the Expo app showed nothing
        // until you pressed Start).
        viewModelScope.launch {
            combine(
                visible,
                controller.state.map { it.active == null }.distinctUntilChanged(),
                settings.map { it.locationSource }.distinctUntilChanged(),
                permissionVersion,
            ) { vis, idle, source, _ -> Triple(vis, idle, source) }
                .flatMapLatest { (vis, idle, source) ->
                    if (vis && idle && LocationPermissions.hasAny(context)) {
                        sources.forMode(source).updates(1000).catch { }
                    } else emptyFlow()
                }
                .collect { previewFix.value = it }
        }
    }

    fun setVisible(v: Boolean) {
        if (v == visible.value) return
        visible.value = v
        if (v) recognizer.acquire() else recognizer.release()
    }
    fun onPermissionsChanged() { permissionVersion.value++ }
    fun select(activity: ActivityType) { selected.value = activity }

    fun start() {
        controller.start(ui.value.activity)
    }

    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun lap() = controller.lap()

    fun stop() = viewModelScope.launch {
        val id = controller.stop()
        events.send(if (id != null) TrackEvent.Saved(id) else TrackEvent.NothingRecorded)
    }

    fun discard() = viewModelScope.launch {
        controller.stop()?.let { sessions.delete(it) }
        events.send(TrackEvent.Discarded)
    }

    fun toggleUnits() = viewModelScope.launch {
        val next = if (settings.value.unitSystem == UnitSystem.Metric) UnitSystem.Imperial else UnitSystem.Metric
        settingsRepository.setUnitSystem(next)
    }

    fun clearError() = controller.clearError()
}

private data class Extras(
    val preview: Fix?,
    val sensors: SensorLive?,
    val fitness: FitnessLive?,
    val bpm: Int?,
    val detected: DetectedMotion?,
)

sealed interface TrackEvent {
    data class Saved(val sessionId: String) : TrackEvent
    data object NothingRecorded : TrackEvent
    data object Discarded : TrackEvent
}
