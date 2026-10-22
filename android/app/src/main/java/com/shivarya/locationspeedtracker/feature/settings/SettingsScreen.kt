package com.shivarya.locationspeedtracker.feature.settings

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.BuildConfig
import com.shivarya.locationspeedtracker.core.settings.LocationSourceMode
import com.shivarya.locationspeedtracker.core.settings.MapEngine
import com.shivarya.locationspeedtracker.core.settings.MapType
import com.shivarya.locationspeedtracker.core.settings.Palette
import com.shivarya.locationspeedtracker.core.settings.ThemeMode
import com.shivarya.locationspeedtracker.core.share.AppShare
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.theme.supportsDynamicColor

/** Settings → other screens; a null entry hides its row. */
data class SettingsNav(
    val openGps: () -> Unit,
    val openOffline: (() -> Unit)? = null,
    val openProfile: (() -> Unit)? = null,
    val openHeartRate: (() -> Unit)? = null,
    val openHealth: (() -> Unit)? = null,
    val openBackup: (() -> Unit)? = null,
    val openSensors: (() -> Unit)? = null,
    val openAbout: () -> Unit,
)

const val PRIVACY_URL = "https://shivarya.dev/location-tracker/privacy.html"

@Composable
fun SettingsScreen(nav: SettingsNav, vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.settings
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(com.shivarya.locationspeedtracker.ui.navigation.LocalSnackbar.current) },
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Units") {
                Segmented(UnitSystem.entries, s.unitSystem, { "${it.label} (${if (it == UnitSystem.Metric) "km/h, km, m" else "mph, mi, ft"})" }, vm::setUnits)
                SwitchRow("Show pace for walks, runs and hikes", "min/km or min/mi instead of speed", s.showPace, vm::setShowPace)
            }

            SectionCard(title = "Recording") {
                Text("Location source", style = MaterialTheme.typography.labelLarge)
                LocationSourceMode.entries.forEach { m ->
                    RadioRow(m.label, m.detail, s.locationSource == m) { vm.setLocationSource(m) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Update interval", style = MaterialTheme.typography.labelLarge)
                Segmented(listOf(1000L, 2000L, 5000L), s.intervalMs, { if (it == 5000L) "5 s (saver)" else "${it / 1000} s" }, vm::setInterval)
                Spacer(Modifier.height(8.dp))
                Text("Ignore fixes less accurate than", style = MaterialTheme.typography.labelLarge)
                Segmented(listOf(20, 40, 60, 100), s.accuracyThresholdM, { if (s.unitSystem == UnitSystem.Metric) "$it m" else "${(it * 3.28).toInt()} ft" }, vm::setAccuracy)
                SwitchRow("Automatic splits", "A split every ${if (s.unitSystem == UnitSystem.Metric) "kilometre" else "mile"}", s.autoSplits, vm::setAutoSplits)
                SwitchRow("Auto-pause", "Pause when you stop moving, resume when you go", s.autoPause, vm::setAutoPause)
                SwitchRow("Keep screen on while recording", null, s.keepScreenOn, vm::setKeepScreenOn)
                NavRow("GPS doctor", "Check signal, satellites and settings for the best fix", nav.openGps)
                nav.openSensors?.let { NavRow("Sensors", "Live readings from every sensor on this phone", it) }
            }

            SectionCard(title = "Maps") {
                Text("Map style", style = MaterialTheme.typography.labelLarge)
                Segmented(MapType.entries, s.mapType, { it.label }, vm::setMapType)
                if (nav.openOffline != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Map engine", style = MaterialTheme.typography.labelLarge)
                    MapEngine.entries.forEach { e ->
                        RadioRow(e.label, engineDetail(e), s.mapEngine == e) { vm.setMapEngine(e) }
                    }
                    NavRow("Offline maps", "Download areas to use without a connection", nav.openOffline)
                }
            }

            if (nav.openProfile != null || nav.openHeartRate != null || nav.openHealth != null) {
                SectionCard(title = "Fitness") {
                    nav.openProfile?.let { NavRow("Profile", "Weight, age and heart-rate zones for calories", it) }
                    nav.openHeartRate?.let { NavRow("Heart-rate sensor", s.hrDeviceName ?: "Pair a Bluetooth chest strap or watch", it) }
                    nav.openHealth?.let { NavRow("Health Connect", if (s.healthConnectSync) "Syncing workouts" else "Share workouts, read heart rate from your watch", it) }
                }
            }

            SectionCard(title = "Appearance") {
                Segmented(ThemeMode.entries, s.themeMode, { it.label }, vm::setTheme)
                if (supportsDynamicColor) {
                    Spacer(Modifier.height(8.dp))
                    Segmented(Palette.entries, s.palette, { if (it == Palette.Classic) "SpeedTrack colours" else "Wallpaper colours" }, vm::setPalette)
                }
            }

            SectionCard(title = "Data") {
                Text(
                    "${ui.sessionCount} saved session${if (ui.sessionCount == 1) "" else "s"}. Everything stays on this phone — " +
                        "no account, no server.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ui.legacyImported?.takeIf { it > 0 }?.let {
                    Text("$it imported from the previous version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                nav.openBackup?.let { NavRow("Backup & restore", "Save everything to a file, or restore one", it) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) { Text("Delete all sessions") }
            }

            SectionCard(title = "Spread the word") {
                NavRow("Share SpeedTrack", "Send friends the app, with the links to join the test and install it") {
                    runCatching { context.startActivity(AppShare.intent(context)) }
                }
            }

            SectionCard(title = "About") {
                Text("SpeedTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                NavRow("Privacy policy", null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                NavRow("Map data & open-source licences", null, nav.openAbout)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all sessions?") },
            text = { Text("Every saved session and its route will be permanently deleted. Export a backup first if you might want them.") },
            confirmButton = {
                Button(
                    onClick = { confirmClear = false; vm.clearAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun engineDetail(e: MapEngine) = when (e) {
    MapEngine.Auto -> "Google Maps online; your downloaded offline maps without a connection"
    MapEngine.Google -> "Always Google Maps (cached areas only when offline)"
    MapEngine.Offline -> "Always OpenStreetMap — online, or offline in downloaded areas"
}

@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label(o), maxLines = 1) },
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
