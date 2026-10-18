package com.shivarya.locationspeedtracker.feature.gps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.location.Check
import com.shivarya.locationspeedtracker.core.location.CheckFix
import com.shivarya.locationspeedtracker.core.location.CheckLevel
import com.shivarya.locationspeedtracker.core.location.Constellation
import com.shivarya.locationspeedtracker.core.location.GnssSnapshot
import com.shivarya.locationspeedtracker.core.location.Satellite
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.ui.components.GpsQuality
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.components.SignalBars
import com.shivarya.locationspeedtracker.ui.components.StatGrid
import com.shivarya.locationspeedtracker.ui.components.StatItem
import com.shivarya.locationspeedtracker.ui.components.color
import com.shivarya.locationspeedtracker.ui.navigation.LocalSnackbar
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GpsDoctorScreen(onBack: () -> Unit, vm: GpsDoctorViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var confirmCold by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { vm.pause() }
    }
    val resolver = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.refresh() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val runFix: (CheckFix) -> Unit = { fix ->
        when (fix) {
            is CheckFix.Resolve -> resolver.launch(IntentSenderRequest.Builder(fix.intentSender).build())
            is CheckFix.RequestPermissions -> permissions.launch(fix.permissions.toTypedArray())
            is CheckFix.OpenSettings -> runCatching { context.startActivity(fix.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("GPS doctor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VerdictCard(ui)
            if (ui.tips.isNotEmpty()) {
                SectionCard(title = "Tips") {
                    ui.tips.forEach { tip ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = SpeedTrackTheme.signal.warning, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tip, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            SectionCard(title = "Checklist") {
                ui.checks.forEach { c -> CheckRow(c, runFix) }
            }
            SectionCard(title = "Sky view") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SkyPlot(ui.gnss.satellites, Modifier.fillMaxWidth(0.85f).widthIn(max = 360.dp).aspectRatio(1f))
                }
                Spacer(Modifier.height(8.dp))
                Legend()
            }
            if (ui.gnss.satellites.isNotEmpty()) {
                SectionCard(title = "Signal strength (C/N₀)") { Cn0Bars(ui.gnss) }
            }
            SectionCard(title = "Assistance data") {
                Text(
                    "If the fix is stuck or far off, refresh the satellite assistance data. A cold start clears everything the " +
                        "receiver remembers — the next fix takes longer, but starts clean.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val ok = vm.refreshAssistance(false)
                        scope.launch { snackbar.showSnackbar(if (ok) "Assistance data requested" else "This phone doesn't accept that command") }
                    }, enabled = ui.online) { Text("Refresh A-GPS") }
                    OutlinedButton(onClick = { confirmCold = true }) { Text("Cold start") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmCold) {
        AlertDialog(
            onDismissRequest = { confirmCold = false },
            title = { Text("Cold start GPS?") },
            text = { Text("Deletes cached satellite data. The next fix may take a minute or more, longer offline.") },
            confirmButton = {
                Button(onClick = {
                    confirmCold = false
                    val ok = vm.refreshAssistance(true)
                    scope.launch { snackbar.showSnackbar(if (ok) "GPS reset — waiting for a fresh fix" else "This phone doesn't accept that command") }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmCold = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VerdictCard(ui: DoctorUi) {
    val f = ui.fix
    val g = ui.gnss
    val q = GpsQuality.of(f?.hAcc)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalBars(q.bars, q.color(), Modifier.size(width = 36.dp, height = 28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(ui.verdict.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${g.used} of ${g.visible} satellites in use" + (g.topCn0?.let { " · ${it.toInt()} dB-Hz" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val units = ui.units
        StatGrid(
            listOf(
                StatItem("Horizontal", f?.hAcc?.let { "±" + Units.shortDistance(it, units).value.fmt(1) } ?: "–", Units.shortDistance(0.0, units).unit),
                StatItem("Vertical", f?.vAcc?.let { "±" + Units.shortDistance(it, units).value.fmt(1) } ?: "–", Units.shortDistance(0.0, units).unit),
                StatItem("Speed acc.", f?.speedAcc?.let { "±" + Units.speed(it, units).value.fmt(1) } ?: "–", Units.speedUnit(units)),
                StatItem("HDOP", g.dop?.hdop?.fmt(1) ?: "–"),
                StatItem("VDOP", g.dop?.vdop?.fmt(1) ?: "–"),
                StatItem("First fix", g.ttffMs?.let { (it / 1000.0).fmt(1) } ?: "–", "s"),
                StatItem("Altitude (MSL)", Format.altitude(f?.altitudeMsl ?: f?.altitude, units)),
                StatItem("Dual-frequency", if (g.dualFrequency) "L1 + L5" else if (g.satellites.any { it.isL5 }) "L5 visible" else "L1 only"),
                StatItem("Systems", g.constellationsUsed.size.toString()),
            ),
            columns = 3,
        )
    }
}

@Composable
private fun CheckRow(c: Check, onFix: (CheckFix) -> Unit) {
    val s = SpeedTrackTheme.signal
    val (icon, tint) = when (c.level) {
        CheckLevel.Ok -> Icons.Filled.CheckCircle to s.excellent
        CheckLevel.Info -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        CheckLevel.Warn -> Icons.Filled.Warning to s.fair
        CheckLevel.Fail -> Icons.Filled.Error to s.bad
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = c.level.name, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleSmall)
            Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (c.fix != null && c.fixLabel != null) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { onFix(c.fix) }) { Text(c.fixLabel) }
        }
    }
}

@Composable
fun constellationColor(c: Constellation): Color = when (c) {
    Constellation.Gps -> Color(0xFF2E86FF)
    Constellation.Glonass -> Color(0xFFE74C3C)
    Constellation.Galileo -> Color(0xFF00D4A0)
    Constellation.Beidou -> Color(0xFFF1C40F)
    Constellation.Qzss -> Color(0xFFB57BFF)
    Constellation.Navic -> Color(0xFFFF8A3D)
    Constellation.Sbas -> Color(0xFF95A5A6)
    Constellation.Unknown -> MaterialTheme.colorScheme.outline
}

/** Polar sky plot: north up, zenith in the centre, horizon at the rim. Filled = used in the fix. */
@Composable
fun SkyPlot(sats: List<Satellite>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = Constellation.entries.associateWith { constellationColor(it) }
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.92f
        val c = center
        listOf(1f, 2f / 3f, 1f / 3f).forEach { f -> drawCircle(grid, r * f, c, style = Stroke(1.5f)) }
        drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        // North marker
        drawCircle(label, 4.dp.toPx(), Offset(c.x, c.y - r - 2.dp.toPx()))
        sats.forEach { s ->
            if (s.elevation < 0) return@forEach
            val dist = r * (90f - s.elevation.coerceIn(0f, 90f)) / 90f
            val a = Math.toRadians(s.azimuth.toDouble() - 90)
            val p = Offset(c.x + (dist * cos(a)).toFloat(), c.y + (dist * sin(a)).toFloat())
            val radius = (4 + (s.cn0.coerceIn(0f, 50f) / 50f) * 6).dp.toPx()
            val col = colors.getValue(s.constellation)
            if (s.usedInFix) drawCircle(col, radius, p) else drawCircle(col.copy(alpha = 0.8f), radius, p, style = Stroke(2.dp.toPx()))
            if (s.isL5) drawCircle(Color.White, radius + 3.dp.toPx(), p, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        listOf(Constellation.Gps, Constellation.Glonass, Constellation.Galileo, Constellation.Beidou, Constellation.Navic).forEach { c ->
            val col = constellationColor(c)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(col) }
                Spacer(Modifier.width(4.dp))
                Text(c.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text(
        "Filled = used in fix · ring = L5 signal · bigger = stronger",
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Cn0Bars(g: GnssSnapshot) {
    val sats = g.satellites.filter { it.cn0 > 0 }.sortedWith(compareBy({ it.constellation.ordinal }, { -it.cn0 }))
    val grid = MaterialTheme.colorScheme.outlineVariant
    val colors = Constellation.entries.associateWith { constellationColor(it) }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = sats.size.coerceAtLeast(1)
        val w = size.width / n
        listOf(20f, 30f, 40f).forEach { level ->
            val y = size.height * (1 - level / 50f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        sats.forEachIndexed { i, s ->
            val h = size.height * (s.cn0.coerceIn(0f, 50f) / 50f)
            val col = colors.getValue(s.constellation)
            drawRect(
                color = if (s.usedInFix) col else col.copy(alpha = 0.35f),
                topLeft = Offset(i * w + w * 0.15f, size.height - h),
                size = androidx.compose.ui.geometry.Size(w * 0.7f, h),
            )
        }
    }
    Text(
        "Lines at 20 / 30 / 40 dB-Hz. Below 25 is weak (indoors, under trees); 35+ is open sky.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
