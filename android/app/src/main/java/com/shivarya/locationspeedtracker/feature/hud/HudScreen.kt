package com.shivarya.locationspeedtracker.feature.hud

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.feature.track.TrackViewModel
import kotlinx.coroutines.delay

private val HudColor = Color(0xFF00E5FF)

/**
 * Head-up display: lay the phone flat under the windshield and the glass reflects the speed. Mirrored by default
 * (tap to flip), black background, maximum brightness, screen kept on, landscape.
 */
@Composable
fun HudScreen(onBack: () -> Unit, vm: TrackViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var mirrored by rememberSaveable { mutableStateOf(true) }
    var showHint by rememberSaveable { mutableStateOf(true) }
    val activity = LocalActivity.current

    LifecycleResumeEffect(Unit) {
        vm.setVisible(true)
        onPauseOrDispose { vm.setVisible(false) }
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation
        val previousBrightness = window?.attributes?.screenBrightness
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = 1f }
            w.decorView.keepScreenOn = true
            WindowCompat.getInsetsController(w, w.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = previousBrightness ?: -1f }
                w.decorView.keepScreenOn = false
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(4000)
        showHint = false
    }

    val units = ui.settings.unitSystem
    val speed = Units.speed((ui.speedMps ?: 0.0).let { if (it < 0.3) 0.0 else it }, units)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).clickable { mirrored = !mirrored },
        contentAlignment = Alignment.Center,
    ) {
        val h = maxHeight.value
        val big = (h * 0.62f).sp
        Column(
            Modifier.graphicsLayer { scaleX = if (mirrored) -1f else 1f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (ui.fix == null) "––" else speed.value.fmt(0), color = HudColor, fontSize = big, fontWeight = FontWeight.Bold, lineHeight = big)
                Text(" ${speed.unit}", color = HudColor.copy(alpha = 0.7f), fontSize = (h * 0.08f).sp, modifier = Modifier.padding(bottom = 24.dp))
            }
            ui.live.active?.let { a ->
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Text(Format.distance(ui.live.stats.distanceM, units), color = HudColor.copy(alpha = 0.8f), fontSize = 28.sp)
                    Text(Format.duration(a.elapsedMs(ui.now) / 1000.0), color = HudColor.copy(alpha = 0.8f), fontSize = 28.sp)
                }
            }
        }
        if (showHint) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                Text("Tap to flip the mirror · Back to exit", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
        // System bars are hidden, so offer an explicit way out.
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopStart) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = "Exit HUD", tint = Color.White.copy(alpha = 0.35f)) }
        }
    }
}
