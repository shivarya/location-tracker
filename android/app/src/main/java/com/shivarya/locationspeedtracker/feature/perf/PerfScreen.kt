package com.shivarya.locationspeedtracker.feature.perf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.data.db.PerfRunEntity
import com.shivarya.locationspeedtracker.core.perf.PerfMode
import com.shivarya.locationspeedtracker.core.perf.PerfResult
import com.shivarya.locationspeedtracker.core.perf.PerfState
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.feature.settings.SwitchRow
import com.shivarya.locationspeedtracker.ui.components.LineChart
import com.shivarya.locationspeedtracker.ui.components.NoticeCard
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.components.StatGrid
import com.shivarya.locationspeedtracker.ui.components.StatItem
import com.shivarya.locationspeedtracker.ui.components.seriesOf
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun PerfScreen(onBack: () -> Unit, vm: PerfViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val units = settings.unitSystem
    LifecycleResumeEffect(Unit) {
        vm.start()
        onPauseOrDispose { vm.stop() }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    if (!settings.perfDisclaimerAccepted) {
        AlertDialog(
            onDismissRequest = onBack,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Use on closed roads only") },
            text = {
                Text(
                    "Performance runs belong on a track or a closed road, never in traffic. Mount the phone securely " +
                        "before you start and don't touch it while driving — SpeedTrack arms, starts and stops on its own. " +
                        "Times come from phone GNSS and are estimates.",
                )
            },
            confirmButton = { Button(onClick = { vm.acceptDisclaimer() }) { Text("I understand") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance timer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerfMode.presets(units).forEach { m ->
                        FilterChip(selected = ui.mode?.stored == m.stored, onClick = { vm.select(m) }, label = { Text(m.label) })
                    }
                }
            }
            item { LiveCard(ui, units, onRearm = vm::rearm) }
            if (ui.rateHz in 0.1..1.5) {
                item {
                    NoticeCard(
                        "1 Hz GPS",
                        "This phone reports position once a second; times are interpolated between fixes (typically within ±0.1 s).",
                        Icons.Filled.Warning,
                    )
                }
            }
            ui.lastResult?.let { r -> item { ResultCard(r, units) } }
            item {
                SwitchRow("1-ft rollout", "Standing starts are timed from the first foot of travel, like a drag strip", settings.perfRollout, vm::setRollout)
            }
            if (history.isNotEmpty()) {
                item { Text("History", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(history, key = { it.id }) { run -> HistoryRow(run, units) { vm.delete(run.id) } }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LiveCard(ui: PerfUi, units: UnitSystem, onRearm: () -> Unit) {
    val signal = SpeedTrackTheme.signal
    SectionCard {
        val (headline, color) = when (val s = ui.state) {
            PerfState.Arming -> when (val m = ui.mode) {
                is PerfMode.Braking -> "Get above ${m.label.substringBefore('–')} ${Units.speedUnit(units)}" to signal.fair
                is PerfMode.Accel -> (if (m.fromMps > 0.5) "Slow below ${m.label.substringBefore('–')} ${Units.speedUnit(units)}" else "Come to a stop") to signal.fair
                else -> "Come to a stop" to signal.fair
            }
            PerfState.Ready -> (if (ui.mode is PerfMode.Braking) "Ready — brake hard" else "Ready — go!") to signal.excellent
            is PerfState.Running -> "%.2f s".format(s.elapsedS) to MaterialTheme.colorScheme.primary
            is PerfState.Done -> "%.2f s".format(s.result.seconds) to MaterialTheme.colorScheme.primary
        }
        Text(headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        val speed = Units.speed((ui.fix?.speed ?: 0.0).let { if (it < 0.3) 0.0 else it }, units)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(speed.value.fmt(0), fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text(" ${speed.unit}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 14.dp))
        }
        Text(
            when {
                ui.fix == null -> "Waiting for satellites…"
                else -> "GNSS ${"%.0f".format(ui.rateHz)} Hz · ±${ui.fix.speedAcc?.let { Units.speed(it, units).value.fmt(1) } ?: "?"} ${Units.speedUnit(units)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        if (ui.state is PerfState.Done) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onRearm, modifier = Modifier.fillMaxWidth()) { Text("Run again") }
        }
    }
}

@Composable
private fun ResultCard(r: PerfResult, units: UnitSystem) {
    SectionCard(title = "${r.mode.label} · ${"%.2f".format(r.seconds)} s") {
        StatGrid(
            buildList {
                add(StatItem(if (r.mode is PerfMode.Braking) "Braking distance" else "Distance", Units.shortDistance(r.distanceM, units).value.fmt(1), Units.shortDistance(0.0, units).unit))
                if (r.mode is PerfMode.Distance) add(StatItem("Trap speed", Units.speed(r.endSpeedMps, units).value.fmt(1), Units.speedUnit(units)))
                add(StatItem("Peak G", r.peakG.fmt(2), "g"))
                add(StatItem("GNSS rate", r.rateHz.fmt(1), "Hz"))
            },
            columns = 2,
        )
        if (r.trace.size >= 2) {
            Spacer(Modifier.height(12.dp))
            LineChart(
                "Speed",
                seriesOf(r.trace.map { it.first }, r.trace.map { Units.speed(it.second, units).value }),
                Units.speedUnit(units),
                MaterialTheme.colorScheme.primary,
                zeroBased = true,
            )
        }
        if (r.rollout) Text("Includes 1-ft rollout.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(run: PerfRunEntity, units: UnitSystem, onDelete: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${run.label} · ${"%.2f".format(run.resultS)} s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val extra = if (run.mode.startsWith("dist")) " · trap ${Format.speed(run.endSpeedMps, units)}" else ""
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(run.time)) + extra + " · peak ${(run.peakG ?: 0.0).fmt(2)} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}
