package com.shivarya.locationspeedtracker.feature.track

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.location.LocationPermissions
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.tracking.Split
import com.shivarya.locationspeedtracker.core.tracking.TrackingStatus
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.feature.map.RouteStyle
import com.shivarya.locationspeedtracker.feature.map.TrackMap
import com.shivarya.locationspeedtracker.ui.components.ActivitySelector
import com.shivarya.locationspeedtracker.ui.components.GpsChip
import com.shivarya.locationspeedtracker.ui.components.NoticeCard
import com.shivarya.locationspeedtracker.ui.components.Pill
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.components.SpeedGauge
import com.shivarya.locationspeedtracker.ui.components.StatGrid
import com.shivarya.locationspeedtracker.ui.components.StatItem
import com.shivarya.locationspeedtracker.ui.components.icon
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import com.shivarya.locationspeedtracker.ui.theme.isAppInDarkTheme
import kotlin.math.ceil

/** Navigation out of the Track screen. */
data class TrackNav(
    val openSession: (String) -> Unit,
    val openMap: () -> Unit,
    val openGps: () -> Unit,
    val openPerf: () -> Unit,
    val openHud: () -> Unit,
    val showMessage: (String) -> Unit,
)

@Composable
fun TrackScreen(nav: TrackNav, vm: TrackViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var follow by rememberSaveable { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        vm.setVisible(true)
        permissionVersion++
        vm.onPermissionsChanged()
        onPauseOrDispose { vm.setVisible(false) }
    }
    LaunchedEffect(Unit) {
        vm.eventFlow.collect { e ->
            when (e) {
                is TrackEvent.Saved -> nav.openSession(e.sessionId)
                TrackEvent.NothingRecorded -> nav.showMessage("Nothing was recorded — no GPS fix during the session")
                TrackEvent.Discarded -> nav.showMessage("Session discarded")
            }
        }
    }

    val hasFine = remember(permissionVersion) { LocationPermissions.hasFine(context) }
    val hasAny = remember(permissionVersion) { LocationPermissions.hasAny(context) }
    val locationOn = remember(permissionVersion) { LocationPermissions.isLocationEnabled(context) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedOnce = true
        permissionVersion++
        vm.onPermissionsChanged()
    }
    // Start never waits on these: notifications and step counting are optional extras.
    val extrasLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.start() }
    val requestLocation = {
        val activity = context as? android.app.Activity
        val canAsk = !askedOnce || activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == true
        if (canAsk) {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            // Permanently denied: only the app's settings page can grant it now.
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    val start = {
        if (!hasAny) {
            requestLocation()
        } else {
            fun missing(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, p) != android.content.pm.PackageManager.PERMISSION_GRANTED
            val extras = buildList {
                if (Build.VERSION.SDK_INT >= 33 && missing(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= 29 && ui.activity.onFoot && missing(Manifest.permission.ACTIVITY_RECOGNITION)) add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (extras.isEmpty()) vm.start() else extrasLauncher.launch(extras.toTypedArray())
        }
    }

    val active = ui.live.active
    val recording = active != null && !active.paused
    val view = LocalView.current
    DisposableEffect(active != null && ui.settings.keepScreenOn) {
        view.keepScreenOn = active != null && ui.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Finish session?") },
            text = { Text("${Format.distance(ui.live.stats.distanceM, ui.settings.unitSystem)} in ${Format.duration((active?.elapsedMs(ui.now) ?: 0) / 1000.0)}") },
            confirmButton = {
                Button(onClick = { confirmStop = false; vm.stop() }) { Text("Finish & save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmStop = false; vm.discard() }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { confirmStop = false }) { Text("Keep going") }
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(com.shivarya.locationspeedtracker.ui.navigation.LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active != null) {
                            Pill(
                                if (active.paused) (if (active.autoPaused) "AUTO-PAUSED" else "PAUSED") else "● REC",
                                if (active.paused) SpeedTrackTheme.signal.warning else SpeedTrackTheme.signal.recording,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(active.activity.label, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("SpeedTrack", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                actions = {
                    if (!ui.online) {
                        Icon(Icons.Filled.CloudOff, contentDescription = "Offline", tint = SpeedTrackTheme.signal.poor)
                        Spacer(Modifier.width(8.dp))
                    }
                    GpsChip(ui.fix?.hAcc, satellites = ui.fix?.satellites, onClick = nav.openGps)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = nav.openPerf) { Icon(Icons.Filled.Timer, contentDescription = "Performance timer") }
                    IconButton(onClick = nav.openHud) { Icon(Icons.Filled.Tv, contentDescription = "HUD mode") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Controls(
                status = ui.live.status,
                activity = ui.activity,
                onStart = start,
                onPause = vm::pause,
                onResume = vm::resume,
                onLap = vm::lap,
                onStop = { confirmStop = true },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth > maxHeight && maxWidth > 600.dp
            val notices: @Composable () -> Unit = {
                Notices(
                    hasAny = hasAny,
                    hasFine = hasFine,
                    locationOn = locationOn,
                    error = ui.live.error,
                    onRequest = requestLocation,
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onOpenGps = nav.openGps,
                    onDismissError = vm::clearError,
                )
            }
            val gauge: @Composable (Modifier) -> Unit = { m ->
                Gauge(ui, onToggleUnits = vm::toggleUnits, modifier = m)
            }
            val map: @Composable () -> Unit = {
                MapCard(ui, follow = follow, onFollow = { follow = it }, onExpand = nav.openMap)
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.42f).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        gauge(Modifier.fillMaxWidth())
                    }
                    Column(
                        Modifier.weight(0.58f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        notices()
                        Stats(ui, columns = 3)
                        map()
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    notices()
                    if (active == null) {
                        ActivitySelector(ui.activity, vm::select)
                        ui.suggestion?.let { sug ->
                            AssistChip(
                                onClick = { vm.select(sug) },
                                label = { Text("Looks like ${sug.label.lowercase()} — switch?") },
                                leadingIcon = { Icon(sug.icon, contentDescription = null) },
                            )
                        }
                    }
                    gauge(Modifier.fillMaxWidth(0.82f).align(Alignment.CenterHorizontally).widthIn(max = 420.dp))
                    Stats(ui, columns = 3)
                    ui.live.lastSplit?.let { SplitBanner(it, ui.settings.unitSystem, ui.activity) }
                    map()
                    if (active == null) LocationDetails(ui)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun Notices(
    hasAny: Boolean,
    hasFine: Boolean,
    locationOn: Boolean,
    error: String?,
    onRequest: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenGps: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !hasAny -> NoticeCard(
                title = "Location access needed",
                body = "SpeedTrack measures speed and records routes from your phone's GPS. Your location never leaves this device.",
                icon = Icons.Filled.LocationOff,
                action = { Button(onClick = onRequest) { Text("Allow location") } },
            )
            !hasFine -> NoticeCard(
                title = "Precise location is off",
                body = "With approximate location, speed and distance are wildly inaccurate. Switch to precise.",
                icon = Icons.Filled.GpsFixed,
                action = { Button(onClick = onRequest) { Text("Use precise location") } },
            )
            !locationOn -> NoticeCard(
                title = "Location is turned off",
                body = "Turn on device location so SpeedTrack can get a GPS fix.",
                icon = Icons.Filled.LocationDisabled,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenLocationSettings) { Text("Turn on") }
                        OutlinedButton(onClick = onOpenGps) { Text("GPS doctor") }
                    }
                },
            )
        }
        if (error != null) {
            NoticeCard(
                title = "Location problem",
                body = error,
                icon = Icons.Filled.Warning,
                tint = MaterialTheme.colorScheme.error,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onOpenGps) { Text("Diagnose") }
                        TextButton(onClick = onDismissError) { Text("Dismiss") }
                    }
                },
            )
        }
    }
}

@Composable
private fun Gauge(ui: TrackUi, onToggleUnits: () -> Unit, modifier: Modifier = Modifier) {
    val units = ui.settings.unitSystem
    val raw = ui.speedMps
    val mps = raw?.let { if (it < 0.3) 0.0 else it }
    val display = mps?.let { Units.speed(it, units).value } ?: 0.0
    val baseMax = Units.speed(ui.activity.gaugeMaxKmh / 3.6, units).value.let { ceil(it / 10) * 10 }
    val max = if (display > baseMax) ceil(display / 20) * 20 else baseMax
    val usePace = ui.activity.onFoot && ui.settings.showPace && ui.live.active != null
    val decimals = if (ui.activity.motorized || display >= 100) 0 else 1
    val speedText = mps?.let { display.fmt(decimals) } ?: "––"
    SpeedGauge(
        value = display,
        max = max,
        primary = if (usePace) mps?.let { Format.paceValue(it, units) } ?: "–:––" else speedText,
        unit = if (usePace) "min${Units.paceUnit(units)}" else Units.speedUnit(units),
        caption = if (ui.fix == null) "Waiting for GPS…" else null,
        // Tapping the dial still toggles units (the Expo app's behaviour); the hint lives in the a11y label.
        secondary = if (usePace) "$speedText ${Units.speedUnit(units)}" else null,
        onClick = onToggleUnits,
        modifier = modifier,
    )
}

@Composable
private fun Stats(ui: TrackUi, columns: Int) {
    val units = ui.settings.unitSystem
    val s = ui.live.stats
    val a = ui.live.active
    val items = buildList {
        if (a != null) {
            val d = Units.distance(s.distanceM, units)
            add(StatItem("Distance", d.value.fmt(if (d.value < 100) 2 else 1), d.unit))
            add(StatItem("Time", Format.duration(a.elapsedMs(ui.now) / 1000.0)))
            add(StatItem("Moving", Format.duration(s.movingS)))
            if (ui.activity.onFoot && ui.settings.showPace) {
                add(StatItem("Avg pace", Format.paceValue(s.avgMovingSpeedMps, units), Units.paceUnit(units)))
            } else {
                val avg = Units.speed(s.avgMovingSpeedMps, units)
                add(StatItem("Avg speed", avg.value.fmt(1), avg.unit))
            }
            val max = Units.speed(s.maxSpeedMps, units)
            add(StatItem("Max speed", max.value.fmt(1), max.unit))
            val gain = Units.altitude(s.elevGainM, units)
            add(StatItem("Climb", gain.value.fmt(0), gain.unit))
            ui.heartRate?.let { add(StatItem("Heart rate", it.toString(), "bpm")) }
            ui.calories?.takeIf { it >= 1 }?.let { add(StatItem("Calories", it.fmt(0), "kcal")) }
            ui.sensors?.let { sl ->
                sl.steps?.let { add(StatItem("Steps", "%,d".format(it))) }
                sl.cadence?.takeIf { it > 0 }?.let { add(StatItem("Cadence", it.toDouble().fmt(0), "spm")) }
                if (ui.activity.motorized || ui.activity == ActivityType.Cycle || ui.activity == ActivityType.Other) {
                    add(StatItem("G-force", kotlin.math.hypot(sl.gLong, sl.gLat).toDouble().fmt(2), "g"))
                    add(StatItem("Peak G", sl.peakG.toDouble().fmt(2), "g"))
                }
                if (ui.activity == ActivityType.Motorbike || ui.activity == ActivityType.Cycle) {
                    sl.leanDeg?.let { add(StatItem("Lean", "${kotlin.math.abs(it).toInt()}°", if (it >= 0) "R" else "L")) }
                }
                sl.verticalSpeedMps?.let { v ->
                    val m = Units.verticalSpeed(v.toDouble(), units)
                    add(StatItem("Vertical", m.value.fmt(0), m.unit))
                }
            }
        }
        val alt = ui.fix?.bestAltitude?.let { Units.altitude(it, units) }
        add(StatItem("Altitude", alt?.value?.fmt(0) ?: "–", alt?.unit))
        val bearing = ui.fix?.bearing?.takeIf { (ui.speedMps ?: 0.0) > 0.5 }
        add(StatItem("Heading", bearing?.let { "${Format.compass(it)} ${it.toInt()}°" } ?: "–"))
        val acc = ui.fix?.hAcc?.let { Units.shortDistance(it, units) }
        add(StatItem("Accuracy", acc?.let { "±${it.value.fmt(0)}" } ?: "–", acc?.unit))
    }
    StatGrid(items, columns = columns)
}

@Composable
private fun SplitBanner(split: Split, units: UnitSystem, activity: ActivityType) {
    val speed = if (split.movingS > 0) split.distanceM / split.movingS else 0.0
    val summary = if (activity.onFoot) Format.pace(speed, units) else Format.speed(speed, units)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(8.dp))
            val label = if (split.kind == com.shivarya.locationspeedtracker.core.tracking.SplitKind.Manual) "Lap" else "Split"
            Text(
                "$label ${split.index + 1}: ${Format.distance(split.distanceM, units)} in ${Format.duration((split.endTime - split.startTime) / 1000.0)} · $summary",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MapCard(ui: TrackUi, follow: Boolean, onFollow: (Boolean) -> Unit, onExpand: () -> Unit) {
    val dark = isAppInDarkTheme(ui.settings.themeMode)
    SectionCard(contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp))) {
            TrackMap(
                chunks = ui.live.track.drawableChunks(),
                style = RouteStyle.speed(ui.live.stats.maxSpeedMps),
                mapType = ui.settings.mapType,
                dark = dark,
                current = ui.fix,
                follow = follow,
                onUserPan = { onFollow(false) },
                modifier = Modifier.fillMaxSize(),
            )
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(onClick = onExpand) { Icon(Icons.Filled.Fullscreen, contentDescription = "Full-screen map") }
                if (!follow) {
                    SmallFloatingActionButton(onClick = { onFollow(true) }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Follow my position")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationDetails(ui: TrackUi) {
    val f = ui.fix ?: return
    SectionCard(title = "Position") {
        StatGrid(
            listOf(
                StatItem("Latitude", Format.coordinate(f.lat, 'N', 'S')),
                StatItem("Longitude", Format.coordinate(f.lon, 'E', 'W')),
                StatItem("Fix time", java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(f.time))),
                StatItem("Source", f.provider ?: "–"),
            ),
            columns = 2,
        )
    }
}

@Composable
private fun Controls(
    status: TrackingStatus,
    activity: ActivityType,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLap: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                TrackingStatus.Idle -> Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Icon(activity.icon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (activity == ActivityType.Other) "Start tracking" else "Start ${activity.label.lowercase()}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                is TrackingStatus.Active -> {
                    if (status.paused) {
                        Button(onClick = onResume, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Resume")
                        }
                    } else {
                        FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp)) {
                            Icon(Icons.Filled.Pause, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Pause")
                        }
                        OutlinedButton(onClick = onLap, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(28.dp)) {
                            Icon(Icons.Filled.Flag, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Lap")
                        }
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}
