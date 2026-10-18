package com.shivarya.locationspeedtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.migration.LegacyMigration
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.TrackingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    /** [message]: one-off startup notice (imported history, recovered session) shown as a snackbar. */
    data class Ready(val message: String? = null) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val legacyMigration: LegacyMigration,
    private val sessions: SessionRepository,
    private val engine: TrackingEngine,
) : ViewModel() {

    private val _state = MutableStateFlow<RootState>(RootState.Loading)
    val state: StateFlow<RootState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        viewModelScope.launch {
            // Must finish before the first real frame so an upgraded Expo user sees their history immediately.
            val imported = runCatching { legacyMigration.runIfNeeded() }.getOrDefault(0)
            runCatching { sessions.purgeDeleted() }
            // A session left "recording" with no live recorder: the process died and the service never came back.
            val recovered = if (engine.state.value.active == null) {
                runCatching { sessions.unfinished()?.also { sessions.finalizeOrphan(it) } }.getOrNull()
            } else null
            val message = when {
                imported > 0 -> "Imported $imported session${if (imported == 1) "" else "s"} from the previous version"
                recovered != null -> "Saved a session that was interrupted"
                else -> null
            }
            _state.value = RootState.Ready(message)
        }
    }
}
