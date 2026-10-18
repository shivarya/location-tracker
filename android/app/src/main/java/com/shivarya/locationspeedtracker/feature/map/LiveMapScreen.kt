package com.shivarya.locationspeedtracker.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.settings.MapType
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.feature.settings.SettingsViewModel
import com.shivarya.locationspeedtracker.feature.track.TrackViewModel
import com.shivarya.locationspeedtracker.ui.theme.isAppInDarkTheme

/** Full-screen live map (the Expo app's "Full Screen" modal, now pannable with a follow toggle). */
@Composable
fun LiveMapScreen(onBack: () -> Unit, vm: TrackViewModel = hiltViewModel(), settingsVm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var follow by rememberSaveable { mutableStateOf(true) }
    var layersOpen by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        vm.setVisible(true)
        onPauseOrDispose { vm.setVisible(false) }
    }
    val units = ui.settings.unitSystem
    Box(Modifier.fillMaxSize()) {
        TrackMap(
            chunks = ui.live.track.drawableChunks(),
            style = RouteStyle.speed(ui.live.stats.maxSpeedMps),
            mapType = ui.settings.mapType,
            dark = isAppInDarkTheme(ui.settings.themeMode),
            current = ui.fix,
            follow = follow,
            onUserPan = { follow = false },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.weight(1f))
                Box {
                    FilledTonalIconButton(onClick = { layersOpen = true }) { Icon(Icons.Filled.Layers, contentDescription = "Map type") }
                    DropdownMenu(expanded = layersOpen, onDismissRequest = { layersOpen = false }) {
                        MapType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label, fontWeight = if (t == ui.settings.mapType) FontWeight.Bold else null) },
                                onClick = {
                                    settingsVm.setMapType(t)
                                    layersOpen = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), tonalElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        val speed = Units.speed((ui.speedMps ?: 0.0).let { if (it < 0.3) 0.0 else it }, units)
                        Readout(speed.value.fmt(0), speed.unit)
                        if (ui.live.active != null) {
                            val d = Units.distance(ui.live.stats.distanceM, units)
                            Readout(d.value.fmt(2), d.unit)
                            Readout(Format.duration((ui.live.active?.elapsedMs(ui.now) ?: 0) / 1000.0), "time")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!follow) {
                    SmallFloatingActionButton(onClick = { follow = true }) { Icon(Icons.Filled.MyLocation, contentDescription = "Follow my position") }
                }
            }
        }
    }
}

@Composable
private fun Readout(value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
