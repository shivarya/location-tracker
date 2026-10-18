package com.shivarya.locationspeedtracker.feature.health

import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.health.HealthAvailability
import com.shivarya.locationspeedtracker.core.health.HealthConnectManager
import com.shivarya.locationspeedtracker.core.health.HealthSyncWorker
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.feature.settings.PRIVACY_URL
import com.shivarya.locationspeedtracker.feature.settings.SwitchRow
import com.shivarya.locationspeedtracker.ui.components.NoticeCard
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthUi(
    val availability: HealthAvailability = HealthAvailability.NotInstalled,
    val granted: Set<String> = emptySet(),
    val sync: Boolean = false,
    val unsynced: Int = 0,
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
    val health: HealthConnectManager,
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    private val state = MutableStateFlow(HealthUi())
    val ui: StateFlow<HealthUi> = combine(state, settings.settings, sessions.finished) { s, st, all ->
        s.copy(
            sync = st.healthConnectSync,
            unsynced = all.count { it.healthConnectSyncedAt == null && health.exerciseType(ActivityType.from(it.activity)) != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUi())

    fun refresh() = viewModelScope.launch {
        state.value = state.value.copy(availability = health.availability(), granted = health.granted())
    }

    fun setSync(on: Boolean) = viewModelScope.launch { settings.setHealthConnectSync(on) }

    /** Queue every finished workout not yet in Health Connect (and fetch watch heart rate for each). */
    fun syncAll() = viewModelScope.launch {
        sessions.finishedList()
            .filter { it.healthConnectSyncedAt == null && health.exerciseType(ActivityType.from(it.activity)) != null }
            .forEach { HealthSyncWorker.enqueue(context, it.id) }
    }
}

@Composable
fun HealthScreen(onBack: () -> Unit, vm: HealthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose {}
    }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.refresh()
        if (granted.isNotEmpty()) vm.setSync(true)
    }
    val allGranted = ui.granted.containsAll(vm.health.permissions)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Connect") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Health Connect is Android's shared fitness store. SpeedTrack can save your walks, runs, hikes and rides " +
                    "there — with route, distance, steps, calories and heart rate — and read back heart rate recorded by your " +
                    "watch's app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (ui.availability) {
                HealthAvailability.NotInstalled, HealthAvailability.NeedsUpdate -> NoticeCard(
                    if (ui.availability == HealthAvailability.NotInstalled) "Health Connect isn't installed" else "Health Connect needs an update",
                    "On Android 13 and older it comes from Google Play.",
                    Icons.Filled.HealthAndSafety,
                    action = {
                        Button(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "market://details?id=${HealthConnectManager.PROVIDER_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding".toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text("Get it on Google Play") }
                    },
                )
                HealthAvailability.Available -> {
                    SectionCard {
                        if (!allGranted) {
                            Text("Allow SpeedTrack to write workouts and read heart rate.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { request.launch(vm.health.permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Connect") }
                        }
                        SwitchRow(
                            "Sync finished workouts",
                            "Drives and motorbike rides stay in SpeedTrack only (Health Connect has no type for them).",
                            ui.sync && ui.granted.isNotEmpty(),
                        ) { on -> if (on && ui.granted.isEmpty()) request.launch(vm.health.permissions) else vm.setSync(on) }
                        if (ui.granted.isNotEmpty() && ui.unsynced > 0) {
                            FilledTonalButton(onClick = vm::syncAll) { Text("Sync ${ui.unsynced} earlier workout${if (ui.unsynced == 1) "" else "s"}") }
                        }
                        TextButton(onClick = {
                            val intent = runCatching { HealthConnectClient.getHealthConnectManageDataIntent(context) }.getOrNull()
                                ?: Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text("Open Health Connect") }
                    }
                }
            }
            SectionCard(title = "Watch heart rate (boAt and others)") {
                Text(
                    "1. In your watch's app (for boAt: boAt Crest or boAt Hub) turn on syncing to Health Connect — or to Google " +
                        "Fit, which forwards to Health Connect.\n2. Connect SpeedTrack above.\n3. After a session, SpeedTrack " +
                        "looks for heart rate from that time window — right away and again 3 hours later, since watch apps often " +
                        "sync late. You can also fetch it from a session's menu.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Shown by Health Connect when the user taps "privacy policy" on SpeedTrack's permission screen (required, together
 * with the Android 14 VIEW_PERMISSION_USAGE alias in the manifest).
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeedTrackTheme {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("How SpeedTrack uses health data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "• Writes: your finished workouts (exercise session and route, distance, steps, calories, elevation, speed " +
                            "and heart rate) so other fitness apps can use them.\n" +
                            "• Reads: heart rate recorded by other apps during your SpeedTrack sessions, to show it with the session.\n\n" +
                            "Data stays on your phone. SpeedTrack has no account and no server, and never shares or sells health data.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }) { Text("Read the privacy policy") }
                    TextButton(onClick = { finish() }) { Text("Close") }
                }
            }
        }
    }
}
