package com.shivarya.locationspeedtracker.feature.hr

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.fitness.HrZones
import com.shivarya.locationspeedtracker.core.hr.HeartRateMonitor
import com.shivarya.locationspeedtracker.core.hr.HrConnection
import com.shivarya.locationspeedtracker.core.hr.HrDevice
import com.shivarya.locationspeedtracker.core.hr.HrState
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.feature.profile.ZoneColors
import com.shivarya.locationspeedtracker.ui.components.NoticeCard
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HeartRateViewModel @Inject constructor(
    val monitor: HeartRateMonitor,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private var holding = false

    fun onVisible() {
        val s = settings.value
        if (!holding && s.hrDeviceAddress != null && monitor.hasPermissions()) {
            holding = true
            monitor.acquire(s.hrDeviceAddress, s.hrDeviceName)
        }
    }

    fun onHidden() {
        monitor.stopScan()
        if (holding) {
            holding = false
            monitor.release()
        }
    }

    fun use(d: HrDevice) = viewModelScope.launch {
        monitor.stopScan()
        settingsRepository.setHrDevice(d.address, d.name)
        if (holding) monitor.release()
        holding = true
        monitor.acquire(d.address, d.name)
    }

    fun forget() = viewModelScope.launch {
        settingsRepository.setHrDevice(null, null)
        if (holding) {
            holding = false
            monitor.release()
        }
    }

    override fun onCleared() = onHidden()
}

@Composable
fun HeartRateScreen(onBack: () -> Unit, vm: HeartRateViewModel = hiltViewModel()) {
    val state by vm.monitor.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(settings.hrDeviceAddress) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (vm.monitor.hasPermissions()) vm.monitor.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heart-rate sensor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.monitor.supported) {
                NoticeCard("No Bluetooth LE", "This phone can't connect to Bluetooth heart-rate sensors.", Icons.Filled.Bluetooth)
                return@Column
            }
            if (!vm.monitor.bluetoothOn) {
                NoticeCard(
                    "Bluetooth is off", "Turn on Bluetooth to connect your sensor.", Icons.Filled.Bluetooth,
                    action = { Button(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Bluetooth settings") } },
                )
            }
            settings.hrDeviceAddress?.let { LiveCard(state, settings, onForget = vm::forget) }
            SectionCard(title = if (settings.hrDeviceAddress == null) "Find your sensor" else "Use a different sensor") {
                Text(
                    "Works with any standard Bluetooth heart-rate strap (Polar, Garmin, Wahoo, Coospo…) or a watch in " +
                        "heart-rate broadcast mode. Wake the sensor (wear it or wet the strap), then scan.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (vm.monitor.hasPermissions()) vm.monitor.startScan() else permissions.launch(vm.monitor.permissions().toTypedArray())
                        },
                        enabled = !state.scanning && vm.monitor.bluetoothOn,
                    ) { Text(if (state.scanning) "Scanning…" else "Scan") }
                    if (state.scanning) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.padding(4.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                state.found.forEach { d ->
                    Surface(onClick = { vm.use(d) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleSmall)
                                Text("Signal ${d.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Use", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            SectionCard(title = "Smartwatch (boAt, Noise, Amazfit…)") {
                Text(
                    "Most watches only talk to their own app, so SpeedTrack can't connect to them directly. If that app " +
                        "syncs to Health Connect, turn on Health Connect in Settings: SpeedTrack then adds the watch's heart " +
                        "rate to your sessions after each workout.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LiveCard(state: HrState, settings: AppSettings, onForget: () -> Unit) {
    val max = settings.profile.maxHr(Calendar.getInstance().get(Calendar.YEAR))
    val bpm = state.liveBpm()
    SectionCard(title = settings.hrDeviceName ?: "Sensor") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(bpm?.toString() ?: "––", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("bpm", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when (state.connection) {
                        HrConnection.Connected -> if (state.contact == false) "No skin contact" else "Connected"
                        HrConnection.Connecting -> "Connecting…"
                        HrConnection.Reconnecting -> "Reconnecting…"
                        HrConnection.Idle -> "Not connected"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                state.battery?.let { Text("Battery $it%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (bpm != null) {
            val zone = HrZones.zone(bpm, max)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (bpm.toFloat() / max).coerceIn(0f, 1f) },
                color = ZoneColors[(zone - 1).coerceIn(0, 4)],
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (zone == 0) "Below zone 1" else "Zone $zone · ${HrZones.names[zone - 1]}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "Connects automatically whenever you start a session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onForget) { Text("Forget sensor") }
        }
    }
}
