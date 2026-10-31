package com.shivarya.locationspeedtracker.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.shivarya.locationspeedtracker.MainActivity
import com.shivarya.locationspeedtracker.core.ApplicationScope
import com.shivarya.locationspeedtracker.core.location.LocationPermissions
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.LiveState
import com.shivarya.locationspeedtracker.core.tracking.TrackingController
import com.shivarya.locationspeedtracker.core.tracking.TrackingEngine
import com.shivarya.locationspeedtracker.core.tracking.TrackingService
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.ui.navigation.EXTRA_OPEN
import com.shivarya.locationspeedtracker.ui.navigation.OPEN_TRACK
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuickAccessDeps {
    fun engine(): TrackingEngine
    fun settings(): SettingsRepository
    @ApplicationScope fun scope(): CoroutineScope
}

private fun deps(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, QuickAccessDeps::class.java)

/** Refresh every quick-access surface (widget, tile) after a tracking change. */
object QuickAccess {
    suspend fun refresh(context: Context) {
        runCatching { SpeedWidget().updateAll(context) }
        runCatching { TileService.requestListeningState(context, ComponentName(context, TrackingTileService::class.java)) }
    }
}

// ---- Home-screen widget ------------------------------------------------------------------------------------------

class SpeedWidget : GlanceAppWidget() {
    // Exact: lay out for the size the user actually gave it (a 3×2 cell is only ~110 dp tall on many launchers).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = deps(context)
        val live = d.engine().state.value
        val settings = d.settings().settings.first()
        provideContent { WidgetBody(context, live, settings.unitSystem, settings.lastActivity) }
    }
}

// Single fixed colours: the widget is always dark, and some launchers drew day/night pairs as dark-on-dark.
private val WidgetBg = Color(0xFF1A252F)
private val WidgetAccent = ColorProvider(Color(0xFF00D4FF))
private val WidgetText = ColorProvider(Color(0xFFF5F7F8))
private val WidgetMuted = ColorProvider(Color(0xFFB4C2C9))

@Composable
private fun WidgetBody(context: Context, live: LiveState, units: UnitSystem, lastActivity: ActivityType) {
    val a = live.active
    val size = LocalSize.current
    val tall = size.height >= 150.dp
    val open = actionStartActivity(Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_TRACK))
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetBg).cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = if (tall) 12.dp else 8.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                a == null -> "SpeedTrack"
                a.paused -> "Paused · ${a.activity.label}"
                else -> "● Recording · ${a.activity.label}"
            },
            style = TextStyle(color = WidgetMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        if (a != null) {
            val speed = Units.speed((live.speedMps ?: 0.0).let { if (it < 0.3) 0.0 else it }, units)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(speed.value.fmt(0), style = TextStyle(color = WidgetAccent, fontSize = if (tall) 38.sp else 30.sp, fontWeight = FontWeight.Bold))
                Text(" ${speed.unit}", style = TextStyle(color = WidgetText, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
            Text(
                "${Format.distance(live.stats.distanceM, units)} · ${Format.duration(a.elapsedMs(System.currentTimeMillis()) / 1000.0)}",
                style = TextStyle(color = WidgetText, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (tall) {
                Text(
                    "max ${Format.speed(live.stats.maxSpeedMps, units)}",
                    style = TextStyle(color = WidgetMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(6.dp))
                Button(
                    text = "Stop",
                    onClick = actionStartService(Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP)),
                )
            }
        } else {
            Text("Ready to track", style = TextStyle(color = WidgetText, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(6.dp))
            // A widget tap is one of the cases where Android lets a location foreground service start from the background.
            Button(
                text = "Start ${lastActivity.label.lowercase()}",
                onClick = actionStartService(
                    Intent(context, TrackingService::class.java)
                        .setAction(TrackingService.ACTION_START)
                        .putExtra(TrackingService.EXTRA_ACTIVITY, lastActivity.stored),
                    isForegroundService = true,
                ),
            )
        }
    }
}

class SpeedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpeedWidget()
}

// ---- Quick Settings tile -----------------------------------------------------------------------------------------

/**
 * Start/stop from the notification shade. Starting goes through [StartTrackingActivity]: a tile click is not one of
 * Android 14's exemptions for starting a location service from the background, but a (briefly) visible activity is.
 */
class TrackingTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val d = deps(this)
        if (d.engine().state.value.active != null) {
            d.scope().launch {
                d.engine().stop()
                QuickAccess.refresh(this@TrackingTileService)
            }
            return
        }
        val intent = Intent(this, StartTrackingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val d = deps(this)
        val live = d.engine().state.value
        val a = live.active
        val units = runBlocking { d.settings().current().unitSystem }
        tile.state = if (a != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "SpeedTrack"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                a == null -> "Tap to start"
                a.paused -> "Paused"
                else -> Units.speed(live.speedMps ?: 0.0, units).let { "${it.value.fmt(0)} ${it.unit} · ${Format.distance(live.stats.distanceM, units)}" }
            }
        }
        tile.updateTile()
    }
}

/** Invisible trampoline: starts recording from a visible activity, then gets out of the way. */
@AndroidEntryPoint
class StartTrackingActivity : ComponentActivity() {
    @Inject lateinit var controller: TrackingController
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!LocationPermissions.hasAny(this)) {
            // First run: permissions are requested in the app.
            startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_TRACK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        val activity = intent.getStringExtra(TrackingService.EXTRA_ACTIVITY)?.let { ActivityType.from(it) }
            ?: runBlocking { settings.current().lastActivity }
        if (controller.state.value.active == null) controller.start(activity)
        finish()
    }
}
