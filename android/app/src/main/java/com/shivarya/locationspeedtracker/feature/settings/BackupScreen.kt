package com.shivarya.locationspeedtracker.feature.settings

import android.net.Uri
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import com.shivarya.locationspeedtracker.core.sync.DriveAuth
import com.shivarya.locationspeedtracker.core.sync.JourneySync
import com.shivarya.locationspeedtracker.core.sync.JourneySyncTrigger
import com.shivarya.locationspeedtracker.core.sync.SyncState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.export.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUi(val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(
    val backups: BackupManager,
    private val sync: JourneySync,
    private val auth: DriveAuth,
    private val trigger: JourneySyncTrigger,
) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUi())
    val ui = _ui.asStateFlow()
    val drive: StateFlow<SyncState> = sync.state
    private val _consent = Channel<PendingIntent>(Channel.BUFFERED)
    val consent = _consent.receiveAsFlow()

    /** Google's consent screen the first time (and after access was removed); afterwards straight through. */
    fun connect() = viewModelScope.launch {
        _ui.value = BackupUi(busy = true)
        try {
            val r = auth.authorize(drive.value.account)
            val pi = r.pendingIntent
            if (r.hasResolution() && pi != null) _consent.send(pi) else finishConnect(r.accessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DriveConnect", "Authorization failed", e)
            _ui.value = BackupUi(message = "Couldn't connect to Google Drive: ${e.message}")
        }
    }

    fun onConsent(ok: Boolean, data: Intent?) = viewModelScope.launch {
        if (!ok) {
            _ui.value = BackupUi(message = "Not connected")
            return@launch
        }
        try {
            finishConnect(auth.resultFromIntent(data).accessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DriveConnect", "Consent result failed", e)
            _ui.value = BackupUi(message = "Couldn't connect to Google Drive: ${e.message}")
        }
    }

    private suspend fun finishConnect(token: String?) {
        if (token == null) error("Google didn't grant Drive access")
        sync.connected(token)
        trigger.now()
        _ui.value = BackupUi(message = "Connected — syncing your journeys")
    }

    fun syncNow() {
        trigger.now()
        _ui.value = BackupUi(message = "Syncing…")
    }

    fun disconnect() = viewModelScope.launch {
        sync.disconnect()
        _ui.value = BackupUi(message = "Disconnected. Journeys already in Drive stay there.")
    }

    fun backup(uri: Uri) = viewModelScope.launch {
        _ui.value = BackupUi(busy = true)
        val r = backups.backup(uri)
        _ui.value = BackupUi(message = r.fold({ "Saved $it session${if (it == 1) "" else "s"}" }, { "Backup failed: ${it.message}" }))
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        _ui.value = BackupUi(busy = true)
        val r = backups.restore(uri)
        _ui.value = BackupUi(message = r.fold({ it.message }, { "Restore failed: ${it.message}" }))
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val drive by vm.drive.collectAsStateWithLifecycle()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsent(it.resultCode == Activity.RESULT_OK, it.data)
    }
    LaunchedEffect(Unit) {
        vm.consent.collect { consent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::backup) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & sync") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Google Drive", style = MaterialTheme.typography.titleMedium)
            DriveSection(drive, ui.busy, onConnect = vm::connect, onSync = vm::syncNow, onDisconnect = vm::disconnect)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Backup file", style = MaterialTheme.typography.titleMedium)
            Text(
                "A backup is one file with every session, route, lap, sensor " +
                    "reading, performance run and setting — save it to Google Drive, Downloads or anywhere else.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { create.launch(vm.backups.suggestedName()) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Back up now")
            }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Restore from a backup")
            }
            Text(
                "Restoring adds sessions that aren't already here; nothing is overwritten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.busy) CircularProgressIndicator()
            ui.message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Android's own backup also keeps a copy of your data with your Google account (up to 25 MB).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DriveSection(s: SyncState, busy: Boolean, onConnect: () -> Unit, onSync: () -> Unit, onDisconnect: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (s.account == null) {
        Text(
            "Keep every journey in your Google Drive automatically, and get them back on a new phone. They go to a " +
                "private app folder only SpeedTrack can open — it doesn't appear among your Drive files, and SpeedTrack " +
                "can't see anything else in your Drive.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onConnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Connect Google Drive") }
        return
    }
    Text("Syncing to ${s.account}", style = MaterialTheme.typography.bodyMedium)
    val status = when {
        s.needsReconnect -> null
        s.lastSyncAt != null -> "Last synced ${DateUtils.getRelativeTimeSpanString(s.lastSyncAt)} · ${s.journeys} journey${if (s.journeys == 1) "" else "s"} in Drive"
        else -> "Waiting for the first sync"
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
    s.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (s.needsReconnect) {
        Button(onClick = onConnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reconnect Google Drive") }
    } else {
        Button(onClick = onSync, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sync now") }
    }
    OutlinedButton(onClick = onDisconnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
    Text(
        "New, renamed and recalculated journeys upload by themselves once you're online; deleting one here deletes " +
            "its Drive copy too.",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}
