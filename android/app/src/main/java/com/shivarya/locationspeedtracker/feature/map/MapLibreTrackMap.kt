package com.shivarya.locationspeedtracker.feature.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.maps.MapLibreSetup
import com.shivarya.locationspeedtracker.core.maps.OfflineArea
import com.shivarya.locationspeedtracker.core.maps.OfflineRegionsEntryPoint
import com.shivarya.locationspeedtracker.core.maps.offlineZoomCap
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import dagger.hilt.android.EntryPointAccessors
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val SRC_ROUTE = "st-route"
private const val SRC_POS = "st-pos"
private const val SRC_PTS = "st-pts"
private const val IMG_ARROW = "st-arrow"
/** MapLibre's own default maximum zoom. */
private const val MAX_MAP_ZOOM = 25.5

/**
 * The offline renderer: MapLibre with OpenFreeMap vector tiles — from downloaded areas when there is no connection,
 * or streamed online when no Google Maps key is configured. Same contract as the Google [TrackMap].
 */
@Composable
fun MapLibreTrackMap(
    chunks: List<List<TrackSample>>,
    style: RouteStyle,
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
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val accent = MaterialTheme.colorScheme.primary
    val mapView = remember {
        MapLibreSetup.ensure(context)
        MapView(context).also { it.onCreate(null) }
    }
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

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loaded by remember { mutableStateOf<Style?>(null) }
    val panCallback by rememberUpdatedState(onUserPan)

    // Offline, keep the zoom within what the downloaded areas hold at the camera target, or the map goes blank.
    val offline = !LocalMapEnvironment.current.online
    val regions = remember { EntryPointAccessors.fromApplication(context.applicationContext, OfflineRegionsEntryPoint::class.java).offlineRegions() }
    LaunchedEffect(offline) { if (offline) regions.load() }
    val areas by regions.areas.collectAsStateWithLifecycle()
    val capAreas by rememberUpdatedState(if (offline) areas else null)

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isLogoEnabled = false
                isCompassEnabled = true
                setAllGesturesEnabled(gesturesEnabled)
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) panCallback()
            }
            m.addOnCameraIdleListener { applyZoomCap(m, capAreas) }
            val first = current?.let { LatLng(it.lat, it.lon) } ?: chunks.firstOrNull()?.firstOrNull()?.let { LatLng(it.lat, it.lon) }
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(first ?: LatLng(20.0, 78.0), if (first != null) 15.5 else 3.0))
            map = m
        }
    }
    LaunchedEffect(map, capAreas) {
        map?.let { applyZoomCap(it, capAreas) }
    }
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        loaded = null
        m.setStyle(Style.Builder().fromUri(MapLibreSetup.style(dark))) { s ->
            s.addImage(IMG_ARROW, arrowBitmap(density, accent.toArgb()))
            s.addSource(GeoJsonSource(SRC_ROUTE))
            s.addSource(GeoJsonSource(SRC_PTS))
            s.addSource(GeoJsonSource(SRC_POS))
            s.addLayer(
                LineLayer("st-route-casing", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor("#0F1A24"),
                    PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineOpacity(0.55f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            s.addLayer(
                LineLayer("st-route-line", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor(Expression.toColor(Expression.get("c"))),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            s.addLayer(
                CircleLayer("st-pts", SRC_PTS).withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))),
                    PropertyFactory.circleStrokeColor("#1A252F"),
                    PropertyFactory.circleStrokeWidth(2.5f),
                ),
            )
            s.addLayer(
                CircleLayer("st-pos-halo", SRC_POS).withProperties(
                    PropertyFactory.circleRadius(20f),
                    PropertyFactory.circleColor(hex(accent)),
                    PropertyFactory.circleOpacity(0.18f),
                ),
            )
            s.addLayer(
                SymbolLayer("st-pos-arrow", SRC_POS).withProperties(
                    PropertyFactory.iconImage(IMG_ARROW),
                    PropertyFactory.iconRotate(Expression.get("b")),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                ),
            )
            loaded = s
        }
    }
    LaunchedEffect(loaded, chunks, style) {
        val s = loaded ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(routeFeatures(chunks, style, accent))
    }
    LaunchedEffect(loaded, highlight, showEndpoints, chunks) {
        val s = loaded ?: return@LaunchedEffect
        val pts = buildList {
            if (showEndpoints) {
                chunks.firstOrNull()?.firstOrNull()?.let { add(dot(it, "#27AE60")) }
                chunks.lastOrNull()?.lastOrNull()?.let { add(dot(it, "#E74C3C")) }
            }
            highlight?.let { add(dot(it, "#FFFFFF")) }
        }
        s.getSourceAs<GeoJsonSource>(SRC_PTS)?.setGeoJson(FeatureCollection.fromFeatures(pts))
    }
    LaunchedEffect(loaded, current?.lat, current?.lon, current?.bearing) {
        val s = loaded ?: return@LaunchedEffect
        val c = current
        val fc = if (c == null) FeatureCollection.fromFeatures(emptyList()) else FeatureCollection.fromFeature(
            Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat)).also { it.addNumberProperty("b", c.bearing ?: 0.0) },
        )
        s.getSourceAs<GeoJsonSource>(SRC_POS)?.setGeoJson(fc)
    }
    LaunchedEffect(loaded, current?.lat, current?.lon, follow) {
        val m = map ?: return@LaunchedEffect
        val c = current ?: return@LaunchedEffect
        if (loaded == null || !follow) return@LaunchedEffect
        val zoom = if (m.cameraPosition.zoom < 12) 16.0 else m.cameraPosition.zoom
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), zoom), 700)
    }
    LaunchedEffect(loaded, fitKey) {
        val m = map ?: return@LaunchedEffect
        if (loaded == null || fitKey == null) return@LaunchedEffect
        val all = chunks.flatten()
        if (all.size < 2) return@LaunchedEffect
        val b = LatLngBounds.Builder().apply { all.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(b, (48 * density).toInt())) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun applyZoomCap(m: MapLibreMap, areas: List<OfflineArea>?) {
    val t = m.cameraPosition.target ?: return
    m.setMaxZoomPreference(areas?.let { offlineZoomCap(it, t.latitude, t.longitude) } ?: MAX_MAP_ZOOM)
}

/** Route as short coloured LineStrings (~8 points each) — MapLibre colours per feature, not per vertex. */
private fun routeFeatures(chunks: List<List<TrackSample>>, style: RouteStyle, plain: Color): FeatureCollection {
    val features = ArrayList<Feature>()
    chunks.forEach { chunk ->
        chunk.splitBySegment().forEach { run ->
            if (run.size < 2) return@forEach
            var i = 0
            while (i < run.size - 1) {
                val end = minOf(i + 8, run.size - 1)
                val piece = run.subList(i, end + 1)
                val f = Feature.fromGeometry(LineString.fromLngLats(piece.map { Point.fromLngLat(it.lon, it.lat) }))
                f.addStringProperty("c", hex(style.colorFor(piece[piece.size / 2], plain)))
                features += f
                i = end
            }
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun dot(s: TrackSample, color: String) =
    Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat)).also { it.addStringProperty("c", color) }

private fun hex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())
