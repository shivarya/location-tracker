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
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.settings.MapType
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.core.units.grouped
import com.shivarya.locationspeedtracker.feature.settings.SettingsViewModel
import com.shivarya.locationspeedtracker.feature.track.TrackUi
import com.shivarya.locationspeedtracker.feature.track.TrackViewModel
import com.shivarya.locationspeedtracker.feature.track.flightItems
import com.shivarya.locationspeedtracker.ui.components.StatGrid
import com.shivarya.locationspeedtracker.ui.components.StatItem
import com.shivarya.locationspeedtracker.ui.theme.isAppInDarkTheme

/** Full-screen live map (the Expo app's "Full Screen" modal, now pannable with a follow toggle). */
@Composable
fun LiveMapScreen(onBack: () -> Unit, vm: TrackViewModel = hiltViewModel(), settingsVm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var follow by rememberSaveable { mutableStateOf(true) }
    var layersOpen by remember { mutableStateOf(false) }
    var bearing by remember { mutableFloatStateOf(0f) }
    var northUp by remember { mutableIntStateOf(0) }
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
            northUpKey = northUp,
            onBearingChange = { bearing = it },
            showCompass = false,
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.weight(1f))
                // The maps' own compasses sit under these buttons (or the status bar), so a rotated map gets this
                // north-up control instead; the needle shows where north is.
                val turned = ((bearing % 360f) + 360f) % 360f
                val rotated = minOf(turned, 360f - turned) > 0.5f
                if (rotated) {
                    FilledTonalIconButton(onClick = { northUp++ }) {
                        Icon(Icons.Filled.Navigation, contentDescription = "Turn the map north up", modifier = Modifier.rotate(-bearing))
                    }
                }
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
            if (!follow) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Spacer(Modifier.weight(1f))
                    SmallFloatingActionButton(onClick = { follow = true }) { Icon(Icons.Filled.MyLocation, contentDescription = "Follow my position") }
                }
            }
            // Opaque panel: a translucent one let dark map tiles wash out the numbers.
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val speed = Units.speed((ui.speedMps ?: 0.0).let { if (it < 0.3) 0.0 else it }, units)
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            speed.value.fmt(0),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            " ${speed.unit}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        ui.live.active?.let { a ->
                            Text(
                                if (a.paused) "Paused · ${a.activity.label}" else a.activity.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    StatGrid(liveMapItems(ui), columns = 3)
                }
            }
        }
    }
}

/** Everything the Track screen shows, in the same tiles; flights lead with phase / time aloft / vertical speed. */
private fun liveMapItems(ui: TrackUi): List<StatItem> = buildList {
    val units = ui.settings.unitSystem
    val a = ui.live.active
    val s = ui.live.stats
    val flight = ui.flight
    if (a != null && flight != null) addAll(flightItems(flight, ui.now, units))
    if (a != null) {
        val d = Units.distance(s.distanceM, units)
        add(StatItem("Distance", d.value.fmt(if (d.value < 100) 2 else 0), d.unit))
        add(StatItem("Time", Format.duration(a.elapsedMs(ui.now) / 1000.0)))
        if (flight == null) {
            val avg = Units.speed(s.avgMovingSpeedMps, units)
            add(StatItem("Avg speed", avg.value.fmt(1), avg.unit))
        }
        val max = Units.speed(s.maxSpeedMps, units)
        add(StatItem("Max speed", max.value.fmt(if (flight != null) 0 else 1), max.unit))
    }
    val alt = ui.fix?.bestAltitude?.let { Units.altitude(it, units) }
    add(StatItem("Altitude", alt?.value?.grouped() ?: "–", alt?.unit))
    val bearing = ui.fix?.bearing?.takeIf { (ui.speedMps ?: 0.0) > 0.5 }
    add(StatItem("Heading", bearing?.let { "${Format.compass(it)} ${it.toInt()}°" } ?: "–"))
    if (a == null) {
        val acc = ui.fix?.hAcc?.let { Units.shortDistance(it, units) }
        add(StatItem("Accuracy", acc?.let { "±${it.value.fmt(0)}" } ?: "–", acc?.unit))
    }
}

