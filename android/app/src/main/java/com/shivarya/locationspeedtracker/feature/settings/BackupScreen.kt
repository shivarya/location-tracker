package com.shivarya.locationspeedtracker.feature.settings

import android.net.Uri
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
class BackupViewModel @Inject constructor(val backups: BackupManager) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUi())
    val ui = _ui.asStateFlow()

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
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::backup) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Your sessions exist only on this phone. A backup is one file with every session, route, lap, sensor " +
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
