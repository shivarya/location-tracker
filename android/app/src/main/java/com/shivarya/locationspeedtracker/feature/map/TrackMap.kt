package com.shivarya.locationspeedtracker.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.shivarya.locationspeedtracker.R
import com.shivarya.locationspeedtracker.core.settings.MapType
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import com.shivarya.locationspeedtracker.ui.components.speedRamp
import com.google.maps.android.compose.MapType as GMapType

enum class RouteColor(val label: String) { Speed("Speed"), Elevation("Elevation"), Plain("Plain") }

/** Everything a map needs to draw a route; shared by the Google and offline (MapLibre) renderers. */
data class RouteStyle(
    val colorBy: RouteColor = RouteColor.Speed,
    /** Value range used for colouring (m/s for speed, m for elevation). */
    val min: Double = 0.0,
    val max: Double = 1.0,
) {
    fun colorFor(s: TrackSample, plain: Color): Color = when (colorBy) {
        RouteColor.Plain -> plain
        RouteColor.Speed -> speedRamp(if (max > min) (s.speedMps - min) / (max - min) else 0.0)
        RouteColor.Elevation -> speedRamp(if (max > min && s.altitude != null) (s.altitude - min) / (max - min) else 0.0)
    }

    companion object {
        /** Speed range bucketed to 5 km/h so finished chunks don't recolour on every new top speed. */
        fun speed(maxMps: Double) = RouteStyle(RouteColor.Speed, 0.0, (kotlin.math.ceil(maxMps * 3.6 / 5).coerceAtLeast(1.0) * 5) / 3.6)

        fun of(colorBy: RouteColor, samples: List<TrackSample>): RouteStyle = when (colorBy) {
            RouteColor.Speed -> speed(samples.maxOfOrNull { it.speedMps } ?: 1.0)
            RouteColor.Elevation -> {
                val alts = samples.mapNotNull { it.altitude }
                RouteStyle(RouteColor.Elevation, alts.minOrNull() ?: 0.0, alts.maxOrNull() ?: 1.0)
            }
            RouteColor.Plain -> RouteStyle(RouteColor.Plain)
        }
    }
}

/**
 * A route map — Google Maps online, MapLibre (OpenFreeMap, offline areas) otherwise; see [MapEnvironment]. [chunks] are drawn as separate polylines (see ChunkedTrack) so a live session only re-renders its
 * short tail. With [follow] the camera tracks [current]; a pan gesture calls [onUserPan] so the caller can drop
 * follow mode and offer a re-centre button. [fitKey] change → fit the camera to the whole route.
 */
@Composable
fun TrackMap(
    chunks: List<List<TrackSample>>,
    style: RouteStyle,
    mapType: MapType,
    dark: Boolean,
    modifier: Modifier = Modifier,
    current: Fix? = null,
    follow: Boolean = false,
    onUserPan: () -> Unit = {},
    fitKey: Any? = null,
    highlight: TrackSample? = null,
    showEndpoints: Boolean = false,
    gesturesEnabled: Boolean = true,
) {
    if (!LocalMapEnvironment.current.useGoogle) {
        MapLibreTrackMap(chunks, style, dark, modifier, current, follow, onUserPan, fitKey, highlight, showEndpoints, gesturesEnabled)
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val accent = MaterialTheme.colorScheme.primary
    val first = current?.let { LatLng(it.lat, it.lon) } ?: chunks.firstOrNull()?.firstOrNull()?.let { LatLng(it.lat, it.lon) }
    val camera = rememberCameraPositionState {
        position = if (first != null) CameraPosition.fromLatLngZoom(first, 16f) else CameraPosition.fromLatLngZoom(LatLng(20.0, 78.0), 3f)
    }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(current?.lat, current?.lon, follow, loaded) {
        val c = current ?: return@LaunchedEffect
        if (!follow || !loaded) return@LaunchedEffect
        val zoom = if (camera.position.zoom < 12f) 16.5f else camera.position.zoom
        runCatching { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), zoom), 700) }
    }
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) onUserPan()
    }
    LaunchedEffect(fitKey, loaded) {
        if (fitKey == null || !loaded) return@LaunchedEffect
        val all = chunks.flatten()
        if (all.isEmpty()) return@LaunchedEffect
        if (all.size == 1) {
            camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(all[0].lat, all[0].lon), 16f))
            return@LaunchedEffect
        }
        val b = LatLngBounds.builder().apply { all.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(b, (48 * density).toInt())) }
    }

    val darkStyle = remember(dark) { if (dark) MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) else null }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(
            mapType = mapType.toGoogle(),
            mapStyleOptions = if (mapType == MapType.Normal) darkStyle else null,
            isBuildingEnabled = true,
        ),
        uiSettings = MapUiSettings(
            compassEnabled = true,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = gesturesEnabled,
            zoomGesturesEnabled = gesturesEnabled,
            rotationGesturesEnabled = gesturesEnabled,
            tiltGesturesEnabled = gesturesEnabled,
        ),
        onMapLoaded = { loaded = true },
    ) {
        val plain = accent
        chunks.forEach { chunk -> RoutePolylines(chunk, style, plain) }

        val arrow = remember { BitmapDescriptorFactory.fromBitmap(arrowBitmap(density, accent.toArgb())) }
        if (showEndpoints) {
            val start = chunks.firstOrNull()?.firstOrNull()
            val end = chunks.lastOrNull()?.lastOrNull()
            val startIcon = remember { BitmapDescriptorFactory.fromBitmap(dotBitmap(density, 0xFF27AE60.toInt())) }
            val endIcon = remember { BitmapDescriptorFactory.fromBitmap(dotBitmap(density, 0xFFE74C3C.toInt())) }
            if (start != null) EndpointMarker(LatLng(start.lat, start.lon), startIcon, "Start")
            if (end != null && end !== start) EndpointMarker(LatLng(end.lat, end.lon), endIcon, "Finish")
        }
        if (highlight != null) {
            val icon = remember { BitmapDescriptorFactory.fromBitmap(dotBitmap(density, 0xFFFFFFFF.toInt())) }
            EndpointMarker(LatLng(highlight.lat, highlight.lon), icon, null)
        }
        if (current != null) {
            val pos = LatLng(current.lat, current.lon)
            current.hAcc?.let { acc ->
                Circle(
                    center = pos,
                    radius = acc,
                    fillColor = accent.copy(alpha = 0.12f),
                    strokeColor = accent.copy(alpha = 0.45f),
                    strokeWidth = 2f,
                )
            }
            val state = rememberUpdatedMarkerState(pos)
            Marker(
                state = state,
                icon = arrow,
                rotation = (current.bearing ?: 0.0).toFloat(),
                flat = true,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                zIndex = 10f,
            )
        }
    }
}

@Composable
private fun RoutePolylines(chunk: List<TrackSample>, style: RouteStyle, plain: Color) {
    // A chunk can span a pause: draw each segment separately so the route isn't joined across the gap.
    val runs = remember(chunk) { chunk.splitBySegment() }
    runs.forEach { run ->
        if (run.size < 2) return@forEach
        val points = remember(run) { run.map { LatLng(it.lat, it.lon) } }
        val spans = remember(run, style, plain) {
            (0 until run.size - 1).map { i ->
                StyleSpan(
                    StrokeStyle.gradientBuilder(style.colorFor(run[i], plain).toArgb(), style.colorFor(run[i + 1], plain).toArgb()).build(),
                )
            }
        }
        Polyline(
            points = points,
            spans = spans,
            width = 14f,
            jointType = JointType.ROUND,
            startCap = RoundCap(),
            endCap = RoundCap(),
        )
    }
}

@Composable
private fun EndpointMarker(pos: LatLng, icon: BitmapDescriptor, title: String?) {
    val state = rememberUpdatedMarkerState(pos)
    Marker(state = state, icon = icon, title = title, anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f), zIndex = 5f)
}

fun List<TrackSample>.splitBySegment(): List<List<TrackSample>> {
    if (isEmpty()) return emptyList()
    val out = mutableListOf<MutableList<TrackSample>>()
    var current = mutableListOf(first())
    for (i in 1 until size) {
        if (this[i].segment != this[i - 1].segment) {
            out += current
            current = mutableListOf()
        }
        current += this[i]
    }
    out += current
    return out
}

private fun MapType.toGoogle() = when (this) {
    MapType.Normal -> GMapType.NORMAL
    MapType.Satellite -> GMapType.SATELLITE
    MapType.Terrain -> GMapType.TERRAIN
    MapType.Hybrid -> GMapType.HYBRID
}

@Composable
fun MapUnavailable(modifier: Modifier = Modifier, message: String = "Map unavailable in this build (no Google Maps key).") {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(
            message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Current-position marker: accent disc with a white ring and a heading arrow (rotated by the map, flat). */
internal fun arrowBitmap(density: Float, accent: Int): Bitmap {
    val size = (34 * density).toInt()
    val bmp = createBitmap(size, size)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    p.color = 0x55000000
    c.drawCircle(r, r + density, r * 0.82f, p)
    p.color = 0xFFFFFFFF.toInt()
    c.drawCircle(r, r, r * 0.8f, p)
    p.color = accent
    c.drawCircle(r, r, r * 0.64f, p)
    p.color = 0xFFFFFFFF.toInt()
    val path = Path().apply {
        moveTo(r, r - r * 0.48f)
        lineTo(r + r * 0.3f, r + r * 0.3f)
        lineTo(r, r + r * 0.12f)
        lineTo(r - r * 0.3f, r + r * 0.3f)
        close()
    }
    c.drawPath(path, p)
    return bmp
}

internal fun dotBitmap(density: Float, color: Int): Bitmap {
    val size = (20 * density).toInt()
    val bmp = createBitmap(size, size)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    p.color = 0xFF1A252F.toInt()
    c.drawCircle(r, r, r * 0.95f, p)
    p.color = color
    c.drawCircle(r, r, r * 0.7f, p)
    return bmp
}
