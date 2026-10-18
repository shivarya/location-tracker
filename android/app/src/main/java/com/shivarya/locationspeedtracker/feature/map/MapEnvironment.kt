package com.shivarya.locationspeedtracker.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.BuildConfig
import com.shivarya.locationspeedtracker.core.location.NetworkMonitor
import com.shivarya.locationspeedtracker.core.settings.MapEngine
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Which renderer maps should use right now. */
data class MapEnvironment(val engine: MapEngine = MapEngine.Auto, val online: Boolean = true) {
    /** Auto: Google while online (needs a key); otherwise MapLibre, which serves downloaded areas offline. */
    val useGoogle: Boolean
        get() = BuildConfig.MAPS_KEY_CONFIGURED && when (engine) {
            MapEngine.Google -> true
            MapEngine.Offline -> false
            MapEngine.Auto -> online
        }
}

val LocalMapEnvironment = staticCompositionLocalOf { MapEnvironment() }

@HiltViewModel
class MapEnvironmentViewModel @Inject constructor(settings: SettingsRepository, network: NetworkMonitor) : ViewModel() {
    val env: StateFlow<MapEnvironment> = combine(settings.settings, network.online) { s, online -> MapEnvironment(s.mapEngine, online) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapEnvironment(online = network.online.value))
}

@Composable
fun ProvideMapEnvironment(vm: MapEnvironmentViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val env by vm.env.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalMapEnvironment provides env, content = content)
}
