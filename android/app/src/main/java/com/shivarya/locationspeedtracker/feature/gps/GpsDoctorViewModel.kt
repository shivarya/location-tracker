package com.shivarya.locationspeedtracker.feature.gps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.location.Check
import com.shivarya.locationspeedtracker.core.location.GnssMonitor
import com.shivarya.locationspeedtracker.core.location.GnssSnapshot
import com.shivarya.locationspeedtracker.core.location.LocationDoctor
import com.shivarya.locationspeedtracker.core.location.LocationPermissions
import com.shivarya.locationspeedtracker.core.location.LocationSources
import com.shivarya.locationspeedtracker.core.location.NetworkMonitor
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.TrackingEngine
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Verdict(val title: String) {
    NoPermission("Location permission needed"),
    Searching("Searching for satellites…"),
    Weak("Weak signal"),
    Poor("Poor fix"),
    Fair("Fair fix"),
    Good("Good fix"),
    Excellent("Excellent fix"),
}

data class DoctorUi(
    val checks: List<Check> = emptyList(),
    val fix: Fix? = null,
    val gnss: GnssSnapshot = GnssSnapshot(),
    val verdict: Verdict = Verdict.Searching,
    val tips: List<String> = emptyList(),
    val units: UnitSystem = UnitSystem.Metric,
    val searchingSinceMs: Long = System.currentTimeMillis(),
    val online: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GpsDoctorViewModel @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
    private val doctor: LocationDoctor,
    private val gnss: GnssMonitor,
    private val sources: LocationSources,
    private val engine: TrackingEngine,
    private val network: NetworkMonitor,
    settings: SettingsRepository,
) : ViewModel() {
    private val checks = MutableStateFlow<List<Check>>(emptyList())
    private val fix = MutableStateFlow<Fix?>(null)
    private val snapshot = MutableStateFlow(GnssSnapshot())
    private val started = System.currentTimeMillis()
    private var streams: Job? = null

    val ui: StateFlow<DoctorUi> = combine(
        checks,
        combine(fix, engine.state) { own, live -> live.fix?.takeIf { live.active != null } ?: own },
        snapshot,
        settings.settings.map { it.unitSystem },
        network.online,
    ) { c, f, g, units, online ->
        val verdict = verdictFor(f, g)
        DoctorUi(c, f, g, verdict, tipsFor(verdict, f, g, online), units, started, online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoctorUi())

    /** Called on resume: re-run the checks and (re)start the fix + satellite streams. */
    fun refresh() {
        viewModelScope.launch { checks.value = doctor.run(network.online.value) }
        if (!LocationPermissions.hasAny(context)) return
        streams?.cancel()
        streams = viewModelScope.launch {
            // Satellites are only reported while the GNSS engine runs, so keep our own request going.
            launch {
                val mode = engine.state.value.active?.let { null } ?: sources.gnss
                if (mode != null) mode.updates(1000).catch { }.collect { fix.value = it }
            }
            launch { gnss.status().catch { }.collect { snapshot.value = it } }
            launch {
                while (true) {
                    delay(15_000)
                    checks.value = doctor.run(network.online.value)
                }
            }
        }
    }

    fun pause() {
        streams?.cancel()
        streams = null
    }

    fun refreshAssistance(coldStart: Boolean): Boolean = doctor.refreshAssistance(coldStart).also {
        if (coldStart) snapshot.value = snapshot.value.copy(ttffMs = null)
    }

    suspend fun currentChecks() = ui.first().checks

    private fun verdictFor(f: Fix?, g: GnssSnapshot): Verdict {
        if (!LocationPermissions.hasAny(context)) return Verdict.NoPermission
        val acc = f?.hAcc
        return when {
            f == null && g.used < 4 -> Verdict.Searching
            (g.topCn0 ?: 0f) < 22f && g.used < 6 && (acc == null || acc > 20) -> Verdict.Weak
            acc == null -> Verdict.Searching
            acc <= 5 -> Verdict.Excellent
            acc <= 10 -> Verdict.Good
            acc <= 20 -> Verdict.Fair
            else -> Verdict.Poor
        }
    }

    private fun tipsFor(v: Verdict, f: Fix?, g: GnssSnapshot, online: Boolean): List<String> = buildList {
        if (f?.isMock == true) add("A mock-location app is feeding this position — turn it off in Developer options for real GPS.")
        when (v) {
            Verdict.Searching, Verdict.Weak -> {
                add("Go outdoors or next to a window — GPS signals barely pass through roofs and walls.")
                add("Stay still for 30–60 seconds while the receiver collects satellite data.")
                if (!online) add("You're offline, so the first fix can take a few minutes (no assisted-GPS download).")
            }
            Verdict.Poor, Verdict.Fair -> {
                add("Tall buildings and dense trees reflect signals; open sky gives the tightest fix.")
                add("Hold the phone with its top edge toward the sky; pockets, bags and cup holders block signal.")
            }
            else -> {}
        }
        if (v != Verdict.NoPermission) {
            add("In a car, mount the phone on the dashboard or windshield — metallic window tint and the roof block GPS.")
        }
        if (g.dualFrequency) add("Dual-frequency (L1 + L5) is active — the best accuracy your phone can do.")
        if (g.constellationsUsed.size >= 3) add("Using ${g.constellationsUsed.joinToString { it.label }} together.")
    }
}
