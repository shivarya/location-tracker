package com.shivarya.locationspeedtracker.feature.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.components.StatGrid
import com.shivarya.locationspeedtracker.ui.components.StatItem
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SensorsScreen(vm: SensorsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val units = settings.unitSystem
    var showAll by remember { mutableStateOf(false) }
    var stepsAsked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(stepsAsked) {
        vm.start()
        onPauseOrDispose { vm.stop() }
    }
    val stepsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.stop()
        stepsAsked = !stepsAsked
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Sensors", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "G-force") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GMeter(ui, Modifier.weight(1f).aspectRatio(1f))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GLabel("Now", "%.2f g".format(kotlin.math.hypot(ui.gNow.long, ui.gNow.lat)))
                            GLabel("Accel", "%.2f".format(ui.maxAccel))
                            GLabel("Brake", "%.2f".format(ui.maxBrake))
                            GLabel("Left", "%.2f".format(ui.maxLeft))
                            GLabel("Right", "%.2f".format(ui.maxRight))
                            TextButton(onClick = vm::resetPeaks) { Text("Reset") }
                        }
                    }
                    Text(
                        if (ui.headingFromGps) "Direction of travel from GPS." else "Direction from the compass — mount the phone facing forward, or drive above 10 km/h for GPS heading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard(title = "Orientation") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Compass(ui.headingDeg, Modifier.weight(1f).aspectRatio(1f))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(ui.headingDeg?.let { "${Format.compass(it)} ${it.toInt()}°" } ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("true north", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            GLabel("Pitch", ui.pitch?.let { "${it.toInt()}°" } ?: "–")
                            GLabel("Roll", ui.roll?.let { "${it.toInt()}°" } ?: "–")
                            GLabel("Lean", ui.lean?.let { "${if (it >= 0) "R" else "L"} ${abs(it).toInt()}°" } ?: "–")
                            FilledTonalButton(onClick = vm::zeroLean) { Text("Zero lean") }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Altitude") {
                    val r = ui.readings
                    StatGrid(
                        listOf(
                            StatItem("Pressure", r.pressureHpa?.let { Units.pressure(it.toDouble(), units).let { m -> m.value.fmt(if (units == UnitSystem.Metric) 1 else 2) } } ?: "–", Units.pressure(0.0, units).unit),
                            StatItem("Baro altitude", ui.baroAltM?.let { Units.altitude(it.toDouble(), units).value.fmt(0) } ?: "–", Units.altitude(0.0, units).unit),
                            StatItem("GPS altitude", ui.gpsAltM?.let { Units.altitude(it, units).value.fmt(0) } ?: "–", Units.altitude(0.0, units).unit),
                            StatItem("Vertical", ui.verticalSpeed?.let { Units.verticalSpeed(it.toDouble(), units).value.fmt(1) } ?: "–", Units.verticalSpeed(0.0, units).unit),
                        ),
                        columns = 2,
                    )
                    if (r.pressureHpa == null) {
                        Text("No barometer on this phone — altitude comes from GPS only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SectionCard(title = "Steps") {
                    when {
                        !ui.stepsAvailable -> Text("This phone has no step counter.", style = MaterialTheme.typography.bodyMedium)
                        !ui.stepsPermitted && Build.VERSION.SDK_INT >= 29 -> {
                            Text("Allow physical-activity access to count steps and cadence.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { stepsPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow") }
                        }
                        else -> StatGrid(
                            listOf(
                                StatItem("Since opened", ui.stepsSinceOpen?.toString() ?: "0"),
                                StatItem("Cadence", ui.cadence?.toDouble()?.fmt(0) ?: "–", "spm"),
                            ),
                            columns = 2,
                        )
                    }
                }
            }
            item {
                SectionCard(title = "Motion") {
                    val r = ui.readings
                    fun v3(a: FloatArray?, d: Int) = a?.joinToString(" / ") { it.toDouble().fmt(d) } ?: "–"
                    StatGrid(
                        listOf(
                            StatItem("Accelerometer x/y/z", v3(r.accel, 1), "m/s²"),
                            StatItem("Gyroscope x/y/z", v3(r.gyroDegPerS, 0), "°/s"),
                            StatItem("Magnetometer x/y/z", v3(r.magnetic, 0), "µT"),
                        ),
                        columns = 1,
                    )
                }
            }
            val env = ui.readings
            if (env.lightLux != null || env.temperatureC != null || env.humidity != null || env.proximityCm != null) {
                item {
                    SectionCard(title = "Environment") {
                        StatGrid(
                            buildList {
                                env.lightLux?.let { add(StatItem("Light", it.toDouble().fmt(0), "lx")) }
                                env.temperatureC?.let { add(StatItem("Temperature", (if (units == UnitSystem.Metric) it.toDouble() else it * 1.8 + 32).fmt(1), if (units == UnitSystem.Metric) "°C" else "°F")) }
                                env.humidity?.let { add(StatItem("Humidity", it.toDouble().fmt(0), "%")) }
                                env.proximityCm?.let { add(StatItem("Proximity", it.toDouble().fmt(0), "cm")) }
                            },
                            columns = 2,
                        )
                    }
                }
            }
            item {
                SectionCard(title = "All sensors (${vm.all.size})") {
                    if (!showAll) {
                        TextButton(onClick = { showAll = true }) { Text("Show every sensor on this phone") }
                    } else {
                        vm.all.forEach { s ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${s.typeName} · ${s.vendor} · range ${s.maxRange.toDouble().fmt(1)} · ${s.powerMa.toDouble().fmt(2)} mA" +
                                        if (s.minDelayUs > 0) " · up to ${1_000_000 / s.minDelayUs} Hz" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Friction circle: up = acceleration, down = braking, sideways = cornering; rings every 0.25 g up to 1 g. */
@Composable
fun GMeter(ui: SensorsUi, modifier: Modifier = Modifier, maxG: Float = 1f) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    val hot = SpeedTrackTheme.signal.bad
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.95f
        val c = center
        for (i in 1..4) drawCircle(grid, r * i / 4, c, style = Stroke(if (i == 4) 2f else 1f))
        drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        fun p(g: GPoint) = Offset(c.x + (g.lat / maxG).coerceIn(-1f, 1f) * r, c.y - (g.long / maxG).coerceIn(-1f, 1f) * r)
        ui.trail.forEachIndexed { i, g -> drawCircle(dot.copy(alpha = 0.1f + 0.5f * i / ui.trail.size), 3.dp.toPx(), p(g)) }
        val now = p(ui.gNow)
        val mag = kotlin.math.hypot(ui.gNow.long, ui.gNow.lat)
        drawCircle(if (mag > 0.5f) hot else dot, 8.dp.toPx(), now)
    }
}

@Composable
fun Compass(headingDeg: Double?, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val north = SpeedTrackTheme.signal.bad
    val needle = MaterialTheme.colorScheme.onSurface
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 * 0.92f
            val c = center
            drawCircle(ring, r, c, style = Stroke(2f))
            // The dial turns so north on it points to real north.
            rotate(-(headingDeg ?: 0.0).toFloat(), c) {
                for (i in 0 until 36) {
                    val a = Math.toRadians(i * 10.0 - 90)
                    val len = if (i % 9 == 0) r * 0.16f else r * 0.07f
                    drawLine(
                        if (i == 0) north else ring,
                        Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()),
                        Offset(c.x + ((r - len) * cos(a)).toFloat(), c.y + ((r - len) * sin(a)).toFloat()),
                        strokeWidth = if (i % 9 == 0) 4f else 2f,
                    )
                }
                val path = Path().apply {
                    moveTo(c.x, c.y - r * 0.7f)
                    lineTo(c.x + r * 0.09f, c.y)
                    lineTo(c.x - r * 0.09f, c.y)
                    close()
                }
                drawPath(path, north)
            }
            // Fixed lubber line: the direction the phone faces.
            drawLine(needle, Offset(c.x, c.y - r * 1.02f), Offset(c.x, c.y - r * 0.78f), strokeWidth = 5f)
        }
    }
}
