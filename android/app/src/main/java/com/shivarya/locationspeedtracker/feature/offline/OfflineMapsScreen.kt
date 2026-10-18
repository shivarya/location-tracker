package com.shivarya.locationspeedtracker.feature.offline

import android.graphics.PointF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.geo.Bounds
import com.shivarya.locationspeedtracker.core.geo.LatLon
import com.shivarya.locationspeedtracker.core.maps.MapDetail
import com.shivarya.locationspeedtracker.core.maps.MapLibreSetup
import com.shivarya.locationspeedtracker.core.maps.OfflineArea
import com.shivarya.locationspeedtracker.core.maps.TileEstimator
import com.shivarya.locationspeedtracker.feature.map.keepGesturesFromParent
import com.shivarya.locationspeedtracker.ui.components.NoticeCard
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale

/** Inset of the selection frame inside the picker map (fraction of each side). */
private const val FRAME_INSET = 0.1f

@Composable
fun OfflineMapsScreen(onBack: () -> Unit, vm: OfflineMapsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var flyTo by remember { mutableStateOf<Bounds?>(null) }
    // +/- buttons: pinching a map inside a scrolling page is fiddly, so the picker also zooms in whole steps.
    val zoomSteps = remember { MutableSharedFlow<Double>(extraBufferCapacity = 8) }
    LaunchedEffect(ui.suggestedName) { if (!nameEdited) name = ui.suggestedName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline maps") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            // Edge-to-edge: the window no longer resizes for the keyboard, so the list makes room itself.
            Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Pan and zoom so the frame covers the area you need, then download it. Without a connection " +
                        "SpeedTrack switches to these maps automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!ui.online) {
                item {
                    NoticeCard("You're offline", "Connect to the internet to download new areas. Downloaded areas keep working.", Icons.Filled.CloudOff)
                }
            }
            item {
                Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(20.dp))) {
                    val start by vm.start.collectAsStateWithLifecycle()
                    AreaPicker(
                        start = start,
                        flyTo = flyTo,
                        zoomSteps = zoomSteps,
                        onBounds = vm::onBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // The frame shows exactly what gets downloaded.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val fits = ui.selection?.fits != false
                        Box(
                            Modifier
                                .padding(horizontal = maxWidth * FRAME_INSET, vertical = maxHeight * FRAME_INSET)
                                .fillMaxSize()
                                .border(BorderStroke(3.dp, if (fits) MaterialTheme.colorScheme.primary else SpeedTrackTheme.signal.bad), RoundedCornerShape(8.dp)),
                        )
                    }
                    Column(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                        FilledTonalIconButton(onClick = { zoomSteps.tryEmit(1.0) }) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
                        FilledTonalIconButton(onClick = { zoomSteps.tryEmit(-1.0) }) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
                    }
                }
            }
            item {
                val sel = ui.selection
                SectionCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        MapDetail.entries.forEachIndexed { i, d ->
                            SegmentedButton(
                                selected = ui.detail == d,
                                onClick = { vm.setDetail(d) },
                                shape = SegmentedButtonDefaults.itemShape(i, MapDetail.entries.size),
                            ) { Text(d.label) }
                        }
                    }
                    Text(
                        ui.detail.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                    if (sel != null) {
                        val color = if (sel.fits) MaterialTheme.colorScheme.onSurface else SpeedTrackTheme.signal.bad
                        Text("≈ ${"%,d".format(sel.tiles)} tiles · ≈ ${mb(sel.bytes)}", style = MaterialTheme.typography.titleMedium, color = color)
                        if (ui.areas.isEmpty()) {
                            Text(
                                "Your first download also fetches the map's fonts and icons, shared by every area.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!sel.fits) {
                            Text(
                                "Too big for one download (max ${"%,d".format(TileEstimator.MAX_TILES)} tiles / ${mb(TileEstimator.MAX_BYTES)}). " +
                                    "Zoom in, choose less detail, or download it in parts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpeedTrackTheme.signal.bad,
                            )
                            sel.fitsAt?.let { d ->
                                TextButton(onClick = { vm.setDetail(d) }) { Text("Fits with ${d.label} detail: use that") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameEdited = true },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.download(name); nameEdited = false },
                        enabled = ui.online && sel?.fits == true,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download this area") }
                }
            }
            if (ui.areas.isNotEmpty()) {
                item {
                    Text(
                        "Downloaded · ${mb(ui.totalBytes)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(ui.areas, key = { it.group }) { a ->
                    AreaRow(a, onShow = { flyTo = a.bounds }, onPause = { vm.pause(a) }, onResume = { vm.resume(a) }, onDelete = { vm.delete(a) })
                }
            }
            item {
                Text(
                    "Maps © OpenStreetMap contributors, © OpenMapTiles, served free by OpenFreeMap. Each area can be up to " +
                        "${"%,d".format(TileEstimator.MAX_TILES)} tiles, per OpenFreeMap's fair-use terms.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AreaRow(a: OfflineArea, onShow: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, onClick = onShow) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val status = when {
                        a.error != null -> a.error
                        a.complete -> "Ready offline · ${a.detail.label} · ${mb(a.tileBytes)}"
                        a.downloading -> "Downloading ${(a.progress * 100).toInt()}% · ${a.detail.label} · ${mb(a.tileBytes)}"
                        else -> "Paused at ${(a.progress * 100).toInt()}%"
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (a.error != null) SpeedTrackTheme.signal.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!a.complete) {
                    IconButton(onClick = if (a.downloading) onPause else onResume) {
                        Icon(if (a.downloading) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (a.downloading) "Pause" else "Resume")
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            if (!a.complete) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { a.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun mb(bytes: Long) = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)

/** A plain MapLibre map that reports the geographic box under the selection frame whenever the camera settles. */
@Composable
private fun AreaPicker(start: LatLon?, flyTo: Bounds?, zoomSteps: SharedFlow<Double>, onBounds: (Bounds) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val report by rememberUpdatedState(onBounds)
    val mapView = remember {
        MapLibreSetup.ensure(context)
        MapView(context).also {
            it.onCreate(null)
            it.keepGesturesFromParent()
        }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(start?.let { LatLng(it.lat, it.lon) } ?: LatLng(20.0, 78.0), if (start != null) 11.5 else 3.0))
            m.setStyle(Style.Builder().fromUri(MapLibreSetup.STYLE_LIGHT))
            m.addOnCameraIdleListener {
                val w = mapView.width.toFloat()
                val h = mapView.height.toFloat()
                if (w <= 0 || h <= 0) return@addOnCameraIdleListener
                val p = m.projection
                val nw = p.fromScreenLocation(PointF(w * FRAME_INSET, h * FRAME_INSET))
                val se = p.fromScreenLocation(PointF(w * (1 - FRAME_INSET), h * (1 - FRAME_INSET)))
                report(Bounds(se.latitude, nw.longitude, nw.latitude, se.longitude))
            }
            map = m
        }
    }
    // The last known location usually arrives just after the map: jump there once.
    var centred by remember { mutableStateOf(start != null) }
    LaunchedEffect(map, start) {
        val m = map ?: return@LaunchedEffect
        val s = start ?: return@LaunchedEffect
        if (centred) return@LaunchedEffect
        centred = true
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lon), 11.5))
    }
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        zoomSteps.collect { m.animateCamera(CameraUpdateFactory.zoomBy(it), 250) }
    }
    LaunchedEffect(map, flyTo) {
        val m = map ?: return@LaunchedEffect
        val b = flyTo ?: return@LaunchedEffect
        val lb = LatLngBounds.Builder().include(LatLng(b.south, b.west)).include(LatLng(b.north, b.east)).build()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(lb, (40 * density).toInt()))
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
