package com.shivarya.locationspeedtracker.feature.perf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.data.db.PerfRunDao
import com.shivarya.locationspeedtracker.core.data.db.PerfRunEntity
import com.shivarya.locationspeedtracker.core.location.LocationSources
import com.shivarya.locationspeedtracker.core.perf.PerfMode
import com.shivarya.locationspeedtracker.core.perf.PerfResult
import com.shivarya.locationspeedtracker.core.perf.PerfSample
import com.shivarya.locationspeedtracker.core.perf.PerfState
import com.shivarya.locationspeedtracker.core.perf.PerformanceTimer
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.Fix
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class PerfUi(
    val mode: PerfMode? = null,
    val state: PerfState = PerfState.Arming,
    val fix: Fix? = null,
    /** Fixes per second actually delivered by the GNSS chip. */
    val rateHz: Double = 0.0,
    val lastResult: PerfResult? = null,
)

@HiltViewModel
class PerfViewModel @Inject constructor(
    private val sources: LocationSources,
    private val runs: PerfRunDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val history = runs.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(PerfUi())
    val ui: StateFlow<PerfUi> = _ui.asStateFlow()
    private var timer: PerformanceTimer? = null
    private var job: Job? = null
    private val recent = ArrayDeque<Long>()

    fun select(mode: PerfMode) {
        timer = PerformanceTimer(mode, settings.value.perfRollout)
        _ui.value = _ui.value.copy(mode = mode, state = PerfState.Arming)
    }

    fun rearm() {
        val m = _ui.value.mode ?: return
        select(m)
    }

    fun setRollout(on: Boolean) = viewModelScope.launch {
        settingsRepository.setPerfRollout(on)
        _ui.value.mode?.let { timer = PerformanceTimer(it, on); _ui.value = _ui.value.copy(state = PerfState.Arming) }
    }

    fun acceptDisclaimer() = viewModelScope.launch { settingsRepository.setPerfDisclaimerAccepted() }

    /** Raw GNSS at the fastest rate the chip offers (many phones 1 Hz, some 5–10 Hz). */
    fun start() {
        if (job != null) return
        job = viewModelScope.launch {
            if (_ui.value.mode == null) select(PerfMode.presets(settingsRepository.settings.first().unitSystem).first())
            sources.gnss.updates(100).catch { }.collect { f -> onFix(f) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun onFix(f: Fix) {
        val now = f.elapsedNanos?.div(1_000_000) ?: f.time
        recent.addLast(now)
        while (recent.size > 2 && now - recent.first() > 3000) recent.removeFirst()
        val rate = if (recent.size >= 2) (recent.size - 1) * 1000.0 / (recent.last() - recent.first()).coerceAtLeast(1) else 0.0
        val t = timer ?: return
        val before = t.state
        // Monotonic clock: wall time can jump when the GNSS chip sets the clock.
        val st = t.onSample(PerfSample(now, f.speed ?: 0.0, f.speedAcc))
        var result = _ui.value.lastResult
        if (st is PerfState.Done && before !is PerfState.Done) {
            result = st.result
            save(st.result)
        }
        _ui.value = _ui.value.copy(state = st, fix = f, rateHz = rate, lastResult = result)
    }

    private suspend fun save(r: PerfResult) {
        val trace = Json.encodeToString(
            ListSerializer(PairSerializer(Double.serializer(), Double.serializer())),
            r.trace.map { (a, b) -> (Math.round(a * 100) / 100.0) to (Math.round(b * 100) / 100.0) },
        )
        runs.insert(
            PerfRunEntity(
                time = System.currentTimeMillis(),
                mode = r.mode.stored,
                label = r.mode.label,
                resultS = r.seconds,
                distanceM = r.distanceM,
                startSpeedMps = r.startSpeedMps,
                endSpeedMps = r.endSpeedMps,
                maxSpeedMps = r.maxSpeedMps,
                peakG = r.peakG,
                rolloutApplied = r.rollout,
                traceJson = trace,
                notes = "%.1f Hz GNSS".format(r.rateHz),
            ),
        )
    }

    fun delete(id: Long) = viewModelScope.launch { runs.delete(id) }

    override fun onCleared() = stop()
}
