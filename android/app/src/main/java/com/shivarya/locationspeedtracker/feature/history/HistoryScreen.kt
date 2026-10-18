package com.shivarya.locationspeedtracker.feature.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.data.dateLine
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.displayTitle
import com.shivarya.locationspeedtracker.core.export.ExportFormat
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.ui.components.EmptyState
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.components.icon
import com.shivarya.locationspeedtracker.ui.navigation.LocalSnackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onOpenSession: (String) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var exportMenu by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }

    LaunchedEffect(Unit) {
        vm.eventFlow.collect { e ->
            when (e) {
                is HistoryEvent.Deleted -> scope.launch {
                    val r = snackbar.showSnackbar(
                        message = if (e.ids.size == 1) "Session deleted" else "${e.ids.size} sessions deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.undoDelete(e.ids)
                }
                is HistoryEvent.Share -> context.startActivity(e.intent)
                is HistoryEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
            }
        }
    }

    val selecting = ui.selection.isNotEmpty()
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(com.shivarya.locationspeedtracker.ui.navigation.LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${ui.selection.size} selected" else "History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (selecting) IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { vm.delete(ui.selection.toList()) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
                    } else {
                        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FileUpload, contentDescription = "Import GPX or SpeedTrack JSON")
                        }
                    }
                    Box {
                        IconButton(onClick = { exportMenu = true }, enabled = ui.sessions.isNotEmpty()) {
                            Icon(if (selecting) Icons.Filled.Share else Icons.Filled.FileDownload, contentDescription = "Export")
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            Text(
                                if (selecting) "Export selected as…" else "Export all as…",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ExportFormat.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(f.label) }, onClick = { exportMenu = false; vm.export(f) })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!ui.loading && ui.sessions.isEmpty() && ui.filter == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.AutoMirrored.Filled.List, "No sessions yet", "Start tracking from the Track tab — finished sessions appear here.")
            }
            return@Scaffold
        }
        val grouped = remember(ui.sessions) { ui.sessions.groupBy { monthKey(it.startTime) } }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Summary(ui) }
            if (ui.activities.size > 1) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = ui.filter == null, onClick = { vm.setFilter(null) }, label = { Text("All") })
                        ui.activities.forEach { a ->
                            FilterChip(
                                selected = ui.filter == a,
                                onClick = { vm.setFilter(a) },
                                label = { Text(a.label) },
                                leadingIcon = { Icon(a.icon, contentDescription = null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
            grouped.forEach { (month, list) ->
                item(key = "h-$month") {
                    Text(
                        month,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(list, key = { it.id }) { s ->
                    SwipeRow(onDelete = { vm.delete(listOf(s.id)) }) {
                        SessionRow(
                            s = s,
                            units = ui.units,
                            selected = s.id in ui.selection,
                            modifier = Modifier.combinedClickable(
                                onClick = { if (selecting) vm.toggleSelect(s.id) else onOpenSession(s.id) },
                                onLongClick = { vm.toggleSelect(s.id) },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(ui: HistoryUi) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { TotalsBlock("This week", ui.week, ui.units) }
            Column(Modifier.weight(1f)) { TotalsBlock("This month", ui.month, ui.units) }
        }
    }
}

@Composable
private fun TotalsBlock(label: String, t: Totals, units: UnitSystem) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(Format.distance(t.distanceM, units), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "${t.sessions} session${if (t.sessions == 1) "" else "s"} · ${Format.duration(t.movingS)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwipeRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
private fun SessionRow(s: SessionEntity, units: UnitSystem, selected: Boolean, modifier: Modifier = Modifier) {
    val activity = ActivityType.from(s.activity)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(activity.icon, contentDescription = activity.label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.displayTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(s.dateLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                val speed = if (activity.onFoot) Format.pace(s.stats.avgMovingSpeedMps, units) else "max ${Format.speed(s.stats.maxSpeedMps, units)}"
                Text(
                    "${Format.distance(s.stats.distanceM, units)} · ${Format.duration(s.elapsedS)} · $speed",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun monthKey(t: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(t))
