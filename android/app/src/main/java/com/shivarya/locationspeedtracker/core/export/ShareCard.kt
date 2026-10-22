package com.shivarya.locationspeedtracker.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.shivarya.locationspeedtracker.core.data.FullSession
import com.shivarya.locationspeedtracker.core.data.dateLine
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.displayTitle
import com.shivarya.locationspeedtracker.core.data.toSample
import com.shivarya.locationspeedtracker.core.maps.MapLibreSetup
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import com.shivarya.locationspeedtracker.core.tracking.displayChunks
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import com.shivarya.locationspeedtracker.feature.map.RouteColor
import com.shivarya.locationspeedtracker.feature.map.RouteStyle
import com.shivarya.locationspeedtracker.feature.map.splitBySegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan

// ---- Pure layout logic (JVM-tested) -------------------------------------------------------------------------------

/** How much "Hide start and finish" trims from each end of the drawn route. */
const val HIDE_ENDS_M = 200.0

/**
 * Drops the first and last [metres] of the route (at most a quarter of it from each end, so short routes keep a
 * middle), so a shared image doesn't show where the user lives. Uses the cumulative [TrackSample.distanceM].
 */
fun trimEnds(samples: List<TrackSample>, metres: Double = HIDE_ENDS_M): List<TrackSample> {
    if (samples.size < 2) return samples
    val start = samples.first().distanceM
    val end = samples.last().distanceM
    val cut = min(metres, (end - start) / 4)
    return samples.filter { it.distanceM >= start + cut && it.distanceM <= end - cut }
}

data class XY(val x: Float, val y: Float)

/**
 * Web Mercator projection of the route, scaled uniformly (no stretching) and centred in a [width]×[height] box with
 * [pad] on every side. Used when there's no map to draw on. A single point lands in the centre.
 */
fun fitRoute(points: List<Pair<Double, Double>>, width: Float, height: Float, pad: Float): List<XY> {
    if (points.isEmpty()) return emptyList()
    val proj = points.map { (lat, lon) -> lon * PI / 180 to ln(tan(PI / 4 + lat * PI / 360)) }
    val minX = proj.minOf { it.first }
    val maxX = proj.maxOf { it.first }
    val minY = proj.minOf { it.second }
    val maxY = proj.maxOf { it.second }
    val spanX = maxX - minX
    val spanY = maxY - minY
    val scale = if (spanX <= 0 && spanY <= 0) 0.0
    else min(if (spanX > 0) (width - 2 * pad) / spanX else Double.MAX_VALUE, if (spanY > 0) (height - 2 * pad) / spanY else Double.MAX_VALUE)
    val offX = (width - spanX * scale) / 2
    val offY = (height - spanY * scale) / 2
    // Mercator y grows northwards; screen y grows downwards.
    return proj.map { (x, y) -> XY((offX + (x - minX) * scale).toFloat(), (offY + (maxY - y) * scale).toFloat()) }
}

data class StatTile(val label: String, val value: String, val unit: String)

private fun timeSeconds(s: SessionEntity) = if (s.stats.movingS > 0) s.stats.movingS else (s.elapsedS - s.pausedS).coerceAtLeast(0.0)

private fun usesPace(s: SessionEntity, showPace: Boolean) = ActivityType.from(s.activity).onFoot && showPace

/** Up to six headline numbers for the card, most important first. */
fun cardStats(s: SessionEntity, units: UnitSystem, showPace: Boolean): List<StatTile> = buildList {
    val dist = Format.distance(s.stats.distanceM, units)
    add(StatTile("Distance", dist.substringBeforeLast(' '), dist.substringAfterLast(' ')))
    add(StatTile(if (s.stats.movingS > 0) "Moving time" else "Time", Format.duration(timeSeconds(s)), ""))
    val avg = s.stats.avgMovingSpeedMps
    if (usesPace(s, showPace)) add(StatTile("Avg pace", Format.paceValue(avg, units), Units.paceUnit(units)))
    else Units.speed(avg, units).let { add(StatTile("Avg speed", it.value.fmt(1), it.unit)) }
    Units.speed(s.stats.maxSpeedMps, units).let { add(StatTile("Max speed", it.value.fmt(1), it.unit)) }
    Units.altitude(s.stats.elevGainM, units).let { add(StatTile("Climb", it.value.fmt(0), it.unit)) }
    when {
        s.calories != null && s.calories > 0 -> add(StatTile("Calories", s.calories.fmt(0), "kcal"))
        s.avgHr != null -> add(StatTile("Avg heart rate", s.avgHr.toString(), "bpm"))
        s.steps != null && s.steps > 0 -> add(StatTile("Steps", "%,d".format(s.steps), ""))
    }
}.take(6)

/** One line for the share sheet's text, e.g. "Morning run · 5.21 km in 28:14 · avg 5:25 /km — SpeedTrack". */
fun shareSummary(s: SessionEntity, units: UnitSystem, showPace: Boolean): String {
    val avg = s.stats.avgMovingSpeedMps
    val pace = if (usesPace(s, showPace)) Format.pace(avg, units) else Format.speed(avg, units)
    return "${s.displayTitle()} · ${Format.distance(s.stats.distanceM, units)} in ${Format.duration(timeSeconds(s))} · avg $pace — SpeedTrack"
}

// ---- Rendering -----------------------------------------------------------------------------------------------------

data class ShareCard(val file: File, val bitmap: Bitmap)

/**
 * Draws a 1080×1350 (4:5) image of a session: the route on a map (MapLibre snapshot of OpenFreeMap tiles, offline
 * from downloaded areas) or, without map data, on the brand gradient; then title, date and headline stats.
 */
@Singleton
class ShareCardRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val export: ExportManager,
) {
    suspend fun render(full: FullSession, settings: AppSettings, hideEnds: Boolean): ShareCard {
        val all = full.points.map { it.toSample() }
        val samples = if (hideEnds) trimEnds(all) else all
        val snapshot = if (samples.size >= 2) withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { snapshot(samples) } else null
        return withContext(Dispatchers.Default) {
            val bitmap = draw(full.session, settings, samples, snapshot, endDots = !hideEnds)
            val file = export.imageFile(full.session)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ShareCard(file, bitmap)
        }
    }

    /** The map image and where each sample lands on it (same order as the samples). */
    private class Snap(val bitmap: Bitmap, val positions: List<XY>)

    /** MapLibre's snapshotter lives on the main thread; null when tiles can't be had (offline outside an area). */
    private suspend fun snapshot(samples: List<TrackSample>): Snap? = withContext(Dispatchers.Main) {
        MapLibreSetup.ensure(context)
        val bounds = LatLngBounds.Builder().apply { samples.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        val opts = MapSnapshotter.Options(W / RATIO, MAP_H / RATIO)
            .withRegion(bounds)
            .withPadding(PAD, PAD, PAD, PAD)
            .withPixelRatio(RATIO.toFloat())
            .withStyleBuilder(Style.Builder().fromUri(MapLibreSetup.style(dark = true)))
            .withLogo(false)
            .withAttribution(false)
        val snapshotter = MapSnapshotter(context, opts)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { snapshotter.cancel() }
            snapshotter.start(
                { snap: MapSnapshot ->
                    // Project inside the callback, while the snapshot is certainly alive. pixelForLatLng answers in the
                    // bitmap's own pixels (already scaled by the pixel ratio; checked on an emulator).
                    val positions = samples.map { p -> snap.pixelForLatLng(LatLng(p.lat, p.lon)).let { XY(it.x, it.y) } }
                    cont.resume(Snap(snap.bitmap.copy(Bitmap.Config.ARGB_8888, false), positions))
                },
                { _: String -> cont.resume(null) },
            )
        }
    }

    private fun draw(s: SessionEntity, settings: AppSettings, samples: List<TrackSample>, snap: Snap?, endDots: Boolean): Bitmap {
        val bmp = createBitmap(W, H)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Map area.
        if (snap != null) {
            c.drawBitmap(snap.bitmap, null, RectF(0f, 0f, W.toFloat(), MAP_H.toFloat()), paint)
        } else {
            paint.shader = LinearGradient(0f, 0f, W.toFloat(), MAP_H.toFloat(), VIOLET_LIGHT, VIOLET_DARK, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), MAP_H.toFloat(), paint)
            paint.shader = null
        }
        if (samples.size >= 2) {
            val xy = snap?.positions ?: fitRoute(samples.map { it.lat to it.lon }, W.toFloat(), MAP_H.toFloat(), 110f)
            drawRoute(c, samples, samples.zip(xy).toMap(), endDots)
        } else {
            text(c, "No route recorded", W / 2f, MAP_H / 2f, 44f, WHITE_70, bold = false, center = true)
        }
        if (snap != null) text(c, "© OpenStreetMap contributors · OpenFreeMap", 28f, MAP_H - 24f, 22f, WHITE_70, bold = false)

        // Panel.
        paint.color = PANEL
        c.drawRect(0f, MAP_H.toFloat(), W.toFloat(), H.toFloat(), paint)
        val activity = ActivityType.from(s.activity)
        val chip = if (activity == ActivityType.Other) "Session" else activity.label
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val chipW = paint.measureText(chip) + 44f
        paint.color = VIOLET
        c.drawRoundRect(RectF(M, MAP_H + 42f, M + chipW, MAP_H + 92f), 25f, 25f, paint)
        text(c, chip, M + 22f, MAP_H + 77f, 28f, WHITE, bold = true)
        text(c, s.displayTitle(), M, MAP_H + 162f, 60f, WHITE, bold = true)
        text(c, s.dateLine(), M, MAP_H + 210f, 30f, MUTED, bold = false)

        val stats = cardStats(s, settings.unitSystem, settings.showPace)
        val colW = (W - 2 * M) / 3f
        stats.forEachIndexed { i, t ->
            val x = M + (i % 3) * colW
            val y = MAP_H + 270f + (i / 3) * 118f
            text(c, t.label, x, y, 27f, MUTED, bold = false)
            val vw = text(c, t.value, x, y + 62f, 54f, WHITE, bold = true)
            if (t.unit.isNotEmpty()) text(c, t.unit, x + vw + 10f, y + 62f, 28f, MUTED, bold = false)
        }

        // Footer: the S-road mark and the app name.
        drawMark(c, W - M - 260f, H - 66f, 44f)
        text(c, "SpeedTrack", W - M - 205f, H - 32f, 40f, WHITE, bold = true)
        return bmp
    }

    private fun drawRoute(c: Canvas, samples: List<TrackSample>, at: Map<TrackSample, XY>, endDots: Boolean) {
        fun project(p: TrackSample) = at.getValue(p)
        val style = RouteStyle.of(RouteColor.Speed, samples)
        val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = CASING
        }
        val line = Paint(casing).apply { strokeWidth = 10f }
        val chunks = displayChunks(samples).flatMap { it.splitBySegment() }.filter { it.size >= 2 }
        chunks.forEach { run ->
            val path = Path()
            run.forEachIndexed { i, p -> project(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
            c.drawPath(path, casing)
        }
        chunks.forEach { run ->
            run.zipWithNext().forEach { (a, b) ->
                line.color = style.colorFor(b, androidx.compose.ui.graphics.Color.White).toArgb()
                val pa = project(a)
                val pb = project(b)
                c.drawLine(pa.x, pa.y, pb.x, pb.y, line)
            }
        }
        if (endDots) {
            dot(c, project(samples.first()), START)
            dot(c, project(samples.last()), FINISH)
        }
    }

    private fun dot(c: Canvas, p: XY, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = WHITE
        c.drawCircle(p.x, p.y, 17f, paint)
        paint.color = color
        c.drawCircle(p.x, p.y, 12f, paint)
    }

    /** The logo's road (branding/speedtrack-mark.svg), [size] px tall with its top-left at [x],[y]. */
    private fun drawMark(c: Canvas, x: Float, y: Float, size: Float) {
        val road = PathParser.createPathFromPathData(ROAD)
        val k = size / 55f // road bounds: x 31..77, y 26.5..81.5
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 15f
            shader = LinearGradient(31f, 26.5f, 77f, 81.5f, VIOLET_LIGHT, VIOLET, Shader.TileMode.CLAMP)
        }
        c.withTranslation(x - 31f * k, y - 26.5f * k) {
            scale(k, k)
            drawPath(road, paint)
        }
    }

    /** Draws [s] with its baseline at [y]; returns its width. */
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean, center: Boolean = false): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (center) textAlign = Paint.Align.CENTER
        }
        c.drawText(s, x, y, paint)
        return paint.measureText(s)
    }

    private companion object {
        const val W = 1080
        const val H = 1350
        const val MAP_H = 800
        const val RATIO = 2
        const val PAD = 70
        const val M = 56f
        const val SNAPSHOT_TIMEOUT_MS = 10_000L
        const val ROAD = "M77,34 H50 C37,34 37,54 50,54 H58 C71,54 71,74 58,74 H31"
        const val WHITE = android.graphics.Color.WHITE
        val WHITE_70 = 0xB3FFFFFF.toInt()
        val PANEL = 0xFF140F24.toInt()
        val VIOLET = 0xFF7C3AED.toInt()
        val VIOLET_LIGHT = 0xFFA78BFA.toInt()
        val VIOLET_DARK = 0xFF4C1D95.toInt()
        val MUTED = 0xFFB9B3D6.toInt()
        val CASING = 0xCC0F0B1E.toInt()
        val START = 0xFF27AE60.toInt()
        val FINISH = 0xFFE74C3C.toInt()
    }
}
