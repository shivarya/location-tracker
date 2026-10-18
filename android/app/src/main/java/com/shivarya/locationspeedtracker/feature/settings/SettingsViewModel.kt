package com.shivarya.locationspeedtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.LocationSourceMode
import com.shivarya.locationspeedtracker.core.settings.MapEngine
import com.shivarya.locationspeedtracker.core.settings.MapType
import com.shivarya.locationspeedtracker.core.settings.Palette
import com.shivarya.locationspeedtracker.core.settings.Profile
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.settings.ThemeMode
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUi(
    /** False until DataStore has delivered the real values (forms must not prefill from the defaults). */
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val sessionCount: Int = 0,
    val legacyImported: Int? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    val ui: StateFlow<SettingsUi> = combine(repo.settings, sessions.finishedCount, repo.legacyImported) { s, n, legacy ->
        SettingsUi(loaded = true, settings = s, sessionCount = n, legacyImported = legacy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setUnits(v: UnitSystem) = viewModelScope.launch { repo.setUnitSystem(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { repo.setThemeMode(v) }
    fun setPalette(v: Palette) = viewModelScope.launch { repo.setPalette(v) }
    fun setMapType(v: MapType) = viewModelScope.launch { repo.setMapType(v) }
    fun setMapEngine(v: MapEngine) = viewModelScope.launch { repo.setMapEngine(v) }
    fun setLocationSource(v: LocationSourceMode) = viewModelScope.launch { repo.setLocationSource(v) }
    fun setInterval(v: Long) = viewModelScope.launch { repo.setIntervalMs(v) }
    fun setAccuracy(v: Int) = viewModelScope.launch { repo.setAccuracyThreshold(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(v) }
    fun setShowPace(v: Boolean) = viewModelScope.launch { repo.setShowPace(v) }
    fun setAutoSplits(v: Boolean) = viewModelScope.launch { repo.setAutoSplits(v) }
    fun setAutoPause(v: Boolean) = viewModelScope.launch { repo.setAutoPause(v) }
    fun setProfile(v: Profile) = viewModelScope.launch { repo.setProfile(v) }
    fun setHealthSync(v: Boolean) = viewModelScope.launch { repo.setHealthConnectSync(v) }
    fun forgetHrDevice() = viewModelScope.launch { repo.setHrDevice(null, null) }

    fun clearAll() = viewModelScope.launch { sessions.deleteAll() }
}
