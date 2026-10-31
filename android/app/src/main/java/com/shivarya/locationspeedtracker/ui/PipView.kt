package com.shivarya.locationspeedtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.tracking.TrackingEngine
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import kotlinx.coroutines.delay

/** The floating picture-in-picture speedometer. */
@Composable
fun PipView(engine: TrackingEngine, units: UnitSystem) {
    val live by engine.state.collectAsStateWithLifecycle()
    val speed = Units.speed((live.speedMps ?: 0.0).let { if (it < 0.3) 0.0 else it }, units)
    // No Surface here, so text would default to black: dark-on-dark in the dark theme.
    val text = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(speed.value.fmt(0), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(" ${speed.unit}", style = MaterialTheme.typography.labelLarge, color = text, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            Format.distance(live.stats.distanceM, units) + (if (live.active?.paused == true) " · paused" else ""),
            style = MaterialTheme.typography.labelMedium,
            color = text,
        )
        // Elapsed and altitude: in a flight these matter as much as the speed.
        val now by produceState(System.currentTimeMillis()) {
            while (true) {
                delay(1_000)
                value = System.currentTimeMillis()
            }
        }
        val line = listOfNotNull(
            live.active?.let { Format.duration(it.elapsedMs(now) / 1000.0) },
            live.fix?.bestAltitude?.let { "alt ${Format.altitude(it, units)}" },
        ).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelSmall, color = text, maxLines = 1)
    }
}
