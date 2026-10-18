package com.shivarya.locationspeedtracker.feature.session

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.data.dateLine
import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionSource
import com.shivarya.locationspeedtracker.core.data.displayTitle
import com.shivarya.locationspeedtracker.core.export.ExportFormat
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.tracking.SplitKind
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.feature.map.RouteColor
import com.shivarya.locationspeedtracker.feature.map.RouteStyle
import com.shivarya.locationspeedtracker.feature.map.TrackMap
import com.shivarya.locationspeedtracker.ui.components.LineChart
import com.shivarya.locationspeedtracker.ui.components.Pill
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.components.StatGrid
import com.shivarya.locationspeedtracker.ui.components.StatItem
import com.shivarya.locationspeedtracker.ui.components.icon
import com.shivarya.locationspeedtracker.ui.components.seriesOf
import com.shivarya.locationspeedtracker.ui.navigation.LocalSnackbar
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import com.shivarya.locationspeedtracker.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(onBack: () -> Unit, vm: SessionViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var activityMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.eventFlow.collect { e ->
            when (e) {
                is SessionEvent.Share -> context.startActivity(e.intent)
                is SessionEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
                SessionEvent.Deleted -> onBack()
            }
        }
    }
    LaunchedEffect(ui.deleted) { if (ui.deleted) onBack() }

    val s = ui.session
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(com.shivarya.locationspeedtracker.ui.navigation.LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = { Text(s?.displayTitle() ?: "", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            ExportFormat.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(f.label) }, onClick = { shareMenu = false; vm.share(f) })
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Notes") }, leadingIcon = { Icon(Icons.Filled.Notes, null) }, onClick = { menu = false; editingNotes = true })
                            DropdownMenuItem(
                                text = { Text("Change activity") },
                                leadingIcon = { Icon(ActivityType.from(s?.activity).icon, null) },
                                onClick = { menu = false; activityMenu = true },
                            )
                            if (vm.healthSupported) {
                                DropdownMenuItem(text = { Text("Save to Health Connect") }, leadingIcon = { Icon(Icons.Filled.HealthAndSafety, null) }, onClick = { menu = false; vm.syncToHealth() })
                            }
                            DropdownMenuItem(text = { Text("Heart rate from watch") }, leadingIcon = { Icon(Icons.Filled.Favorite, null) }, onClick = { menu = false; vm.fetchHeartRate() })
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (s == null || ui.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Body(ui, s, Modifier.padding(padding))
    }

    if (renaming && s != null) {
        TextDialog("Rename session", s.name ?: s.displayTitle(), singleLine = true, onDismiss = { renaming = false }) { vm.rename(it) }
    }
    if (editingNotes && s != null) {
        TextDialog("Notes", s.notes.orEmpty(), singleLine = false, onDismiss = { editingNotes = false }) { vm.setNotes(it) }
    }
    if (activityMenu) {
        AlertDialog(
            onDismissRequest = { activityMenu = false },
            title = { Text("Activity") },
            text = {
                Column {
                    ActivityType.entries.forEach { a ->
                        TextButton(onClick = { vm.setActivity(a); activityMenu = false }, modifier = Modifier.fillMaxWidth()) {
                            Icon(a.icon, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { activityMenu = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete session?") },
            text = { Text("This can't be undone.") },
            confirmButton = { Button(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Body(ui: SessionUi, s: SessionEntity, modifier: Modifier) {
    val units = ui.settings.unitSystem
    val activity = ActivityType.from(s.activity)
    var colorBy by rememberSaveable { mutableStateOf(RouteColor.Speed) }
    var cursorT by remember { mutableStateOf<Double?>(null) }
    var replay by remember { mutableStateOf(false) }
    var replayIndex by remember { mutableIntStateOf(0) }
    val samples = ui.samples
    val style = remember(samples, colorBy) { RouteStyle.of(colorBy, samples) }
    val t0 = samples.firstOrNull()?.time ?: s.startTime

    LaunchedEffect(replay, samples) {
        if (!replay || samples.isEmpty()) return@LaunchedEffect
        // ~20 s for the whole route regardless of its length.
        val step = (samples.size / 400).coerceAtLeast(1)
        while (replayIndex < samples.size - 1) {
            replayIndex = (replayIndex + step).coerceAtMost(samples.size - 1)
            delay(50)
        }
        replay = false
    }
    val highlight: TrackSample? = when {
        replay -> samples.getOrNull(replayIndex)
        cursorT != null -> samples.getOrNull(nearestByTime(samples, t0 + (cursorT!! * 1000).toLong()))
        else -> null
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(activity.label, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(s.dateLine(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (s.sessionSource) {
                SessionSource.Legacy -> { Spacer(Modifier.width(8.dp)); Pill("from v1", SpeedTrackTheme.signal.fair) }
                SessionSource.Import -> { Spacer(Modifier.width(8.dp)); Pill("imported", SpeedTrackTheme.signal.fair) }
                else -> {}
            }
            if (s.healthConnectSyncedAt != null) { Spacer(Modifier.width(8.dp)); Pill("in Health Connect", SpeedTrackTheme.signal.good) }
        }
        if (samples.isNotEmpty()) {
            SectionCard(contentPadding = PaddingValues(0.dp)) {
                Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp))) {
                    TrackMap(
                        chunks = ui.route,
                        style = style,
                        mapType = ui.settings.mapType,
                        dark = isAppInDarkTheme(ui.settings.themeMode),
                        fitKey = s.id,
                        highlight = highlight,
                        showEndpoints = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            if (!replay && replayIndex >= samples.size - 1) replayIndex = 0
                            replay = !replay
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    ) {
                        Icon(if (replay) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (replay) "Pause replay" else "Replay route")
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteColor.entries.forEach { c ->
                        FilterChip(selected = colorBy == c, onClick = { colorBy = c }, label = { Text(c.label) })
                    }
                }
            }
        }
        SectionCard(title = "Summary") { StatGrid(summaryItems(s, activity, units), columns = 3) }
        s.notes?.let { SectionCard(title = "Notes") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        if (samples.size >= 2) {
            SectionCard(title = "Charts") {
                Charts(samples, ui, t0, units, activity, cursorT) { cursorT = it }
            }
        }
        val splits = ui.laps.filter { it.kind == SplitKind.Auto.stored }
        val laps = ui.laps.filter { it.kind == SplitKind.Manual.stored }
        if (splits.isNotEmpty()) SectionCard(title = "Splits (${Units.distance(Units.splitLengthM(units), units).unit})") { LapTable(splits, units, activity) }
        if (laps.isNotEmpty()) SectionCard(title = "Laps") { LapTable(laps, units, activity) }
        Spacer(Modifier.height(16.dp))
    }
}

private fun nearestByTime(samples: List<TrackSample>, t: Long): Int {
    var lo = 0
    var hi = samples.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (samples[mid].time < t) lo = mid + 1 else hi = mid
    }
    return lo
}

private fun summaryItems(s: SessionEntity, activity: ActivityType, units: UnitSystem): List<StatItem> = buildList {
    val st = s.stats
    val d = Units.distance(st.distanceM, units)
    add(StatItem("Distance", d.value.fmt(2), d.unit))
    add(StatItem("Time", Format.duration(s.elapsedS)))
    add(StatItem("Moving", Format.duration(st.movingS)))
    if (activity.onFoot) add(StatItem("Avg pace", Format.paceValue(st.avgMovingSpeedMps, units), Units.paceUnit(units)))
    val avg = Units.speed(st.avgMovingSpeedMps, units)
    add(StatItem("Avg speed", avg.value.fmt(1), avg.unit))
    val max = Units.speed(st.maxSpeedMps, units)
    add(StatItem("Max speed", max.value.fmt(1), max.unit))
    if (st.minMovingSpeedMps > 0) {
        val min = Units.speed(st.minMovingSpeedMps, units)
        add(StatItem("Min speed", min.value.fmt(1), min.unit))
    }
    val gain = Units.altitude(st.elevGainM, units)
    val loss = Units.altitude(st.elevLossM, units)
    add(StatItem("Climb", gain.value.fmt(0), gain.unit))
    add(StatItem("Descent", loss.value.fmt(0), loss.unit))
    st.maxAltM?.let { val a = Units.altitude(it, units); add(StatItem("Highest", a.value.fmt(0), a.unit)) }
    st.minAltM?.let { val a = Units.altitude(it, units); add(StatItem("Lowest", a.value.fmt(0), a.unit)) }
    if (s.pausedS >= 1) add(StatItem("Paused", Format.duration(s.pausedS)))
    s.steps?.let { add(StatItem("Steps", "%,d".format(it))) }
    s.avgCadence?.let { add(StatItem("Cadence", it.fmt(0), "spm")) }
    s.avgHr?.let { add(StatItem("Avg HR", it.toString(), "bpm")) }
    s.maxHr?.let { add(StatItem("Max HR", it.toString(), "bpm")) }
    s.calories?.let { add(StatItem("Calories", it.fmt(0), "kcal")) }
    s.maxG?.let { add(StatItem("Peak G", it.fmt(2), "g")) }
    add(StatItem("GPS quality", "${st.qualityScore}%"))
}

@Composable
private fun Charts(
    samples: List<TrackSample>,
    ui: SessionUi,
    t0: Long,
    units: UnitSystem,
    activity: ActivityType,
    cursorT: Double?,
    onScrub: (Double?) -> Unit,
) {
    val xs = remember(samples) { samples.map { (it.time - t0) / 1000.0 } }
    val speed = remember(samples, units) { seriesOf(xs, samples.map { Units.speed(it.speedMps, units).value }) }
    val accent = MaterialTheme.colorScheme.primary
    val signal = SpeedTrackTheme.signal
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LineChart("Speed", speed, Units.speedUnit(units), accent, cursorX = cursorT, onScrub = onScrub, formatY = { it.fmt(if (activity.motorized) 0 else 1) }, zeroBased = true, minRange = 5.0)
        val alts = samples.mapIndexedNotNull { i, s -> s.altitude?.let { xs[i] to Units.altitude(it, units).value } }
        if (alts.size >= 2) {
            val elev = remember(alts) { seriesOf(alts.map { it.first }, alts.map { it.second }) }
            LineChart("Elevation", elev, Units.altitude(0.0, units).unit, signal.good, cursorX = cursorT, onScrub = onScrub, minRange = 20.0)
        }
        val m = ui.metrics
        val hr = m.filter { it.heartRate != null }
        if (hr.size >= 2) {
            val series = remember(hr) { seriesOf(hr.map { (it.time - t0) / 1000.0 }, hr.map { it.heartRate!!.toDouble() }) }
            LineChart("Heart rate", series, "bpm", signal.bad, cursorX = cursorT, onScrub = onScrub, minRange = 30.0)
        }
        val cad = m.filter { it.cadence != null }
        if (cad.size >= 2) {
            val series = remember(cad) { seriesOf(cad.map { (it.time - t0) / 1000.0 }, cad.map { it.cadence!!.toDouble() }) }
            LineChart("Cadence", series, "spm", signal.fair, cursorX = cursorT, onScrub = onScrub, zeroBased = true, minRange = 40.0)
        }
        val g = m.filter { it.gPeak != null }
        if (g.size >= 2) {
            val series = remember(g) { seriesOf(g.map { (it.time - t0) / 1000.0 }, g.map { it.gPeak!!.toDouble() }) }
            LineChart("G-force (peak)", series, "g", signal.poor, cursorX = cursorT, onScrub = onScrub, formatY = { it.fmt(2) }, zeroBased = true, minRange = 0.5)
        }
        val baro = m.filter { it.baroAltM != null }
        if (baro.size >= 2 && alts.size < 2) {
            val series = remember(baro) { seriesOf(baro.map { (it.time - t0) / 1000.0 }, baro.map { Units.altitude(it.baroAltM!!.toDouble(), units).value }) }
            LineChart("Barometric altitude", series, Units.altitude(0.0, units).unit, signal.good, cursorX = cursorT, onScrub = onScrub, minRange = 20.0)
        }
    }
}

@Composable
private fun LapTable(laps: List<LapEntity>, units: UnitSystem, activity: ActivityType) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("#", Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium)
            Text("Distance", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("Time", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(if (activity.onFoot) "Pace" else "Avg", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("Climb", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium)
        }
        val fastest = laps.filter { it.distanceM > 0 && it.movingS > 0 }.maxByOrNull { it.distanceM / it.movingS }
        laps.forEach { l ->
            val speed = if (l.movingS > 0) l.distanceM / l.movingS else 0.0
            val weight = if (l == fastest && laps.size > 1) FontWeight.Bold else FontWeight.Normal
            Row {
                Text("${l.lapIndex + 1}", Modifier.width(32.dp), fontWeight = weight)
                Text(Format.distance(l.distanceM, units), Modifier.weight(1f), fontWeight = weight)
                Text(Format.duration((l.endTime - l.startTime) / 1000.0), Modifier.weight(1f), fontWeight = weight)
                Text(if (activity.onFoot) Format.paceValue(speed, units) else Units.speed(speed, units).value.fmt(1), Modifier.weight(1f), fontWeight = weight)
                Text(Units.altitude(l.elevGainM, units).value.fmt(0), Modifier.weight(0.8f), fontWeight = weight)
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, singleLine: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onSave(text); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
