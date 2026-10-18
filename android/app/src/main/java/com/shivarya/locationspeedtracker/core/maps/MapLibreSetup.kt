package com.shivarya.locationspeedtracker.core.maps

import android.content.Context
import android.os.Build
import com.shivarya.locationspeedtracker.BuildConfig
import com.shivarya.locationspeedtracker.core.geo.Bounds
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * MapLibre + OpenFreeMap. OpenFreeMap's public instance is free with no key; its terms for offline use are honoured
 * here: a unique User-Agent, at most 4 parallel requests, and downloads capped at 3 000 tiles / 50 MB each
 * (see [TileEstimator] and OfflineRegions). Attribution is drawn by MapLibre from the style.
 */
object MapLibreSetup {
    const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
    private const val AMBIENT_CACHE_BYTES = 100L * 1024 * 1024

    @Volatile private var ready = false

    fun ensure(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            MapLibre.getInstance(app)
            val ua = "SpeedTrack/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID}; Android ${Build.VERSION.RELEASE})"
            val client = OkHttpClient.Builder()
                .dispatcher(Dispatcher().apply { maxRequestsPerHost = 4 })
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build()) }
                .build()
            HttpRequestUtil.setOkHttpClient(client)
            OfflineManager.getInstance(app).setMaximumAmbientCacheSize(AMBIENT_CACHE_BYTES, null)
            ready = true
        }
    }

    fun style(dark: Boolean) = if (dark) STYLE_DARK else STYLE_LIGHT
}

/** Slippy-map tile maths for sizing an offline download before starting it. */
object TileEstimator {
    /** OpenMapTiles vector tiles stop at z14; MapLibre over-zooms them for street-level detail. */
    const val MAX_ZOOM = 14
    /** OpenFreeMap's per-download cap is 3 000 tiles; leave room for style, sprite, glyph and relief tiles. */
    const val MAX_TILES = 2_700
    const val MAX_BYTES = 50L * 1024 * 1024
    /** Rough mean size of an OpenMapTiles vector tile (urban z14 tiles are larger, rural/low-zoom smaller). */
    private const val AVG_TILE_BYTES = 16_000L

    fun x(lon: Double, z: Int): Int = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)

    fun y(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    }

    fun tiles(b: Bounds, minZoom: Int = 0, maxZoom: Int = MAX_ZOOM): Long = (minZoom..maxZoom).sumOf { z ->
        val dx = (x(b.east, z) - x(b.west, z) + 1).toLong()
        val dy = (y(b.south, z) - y(b.north, z) + 1).toLong()
        dx * dy
    }

    fun estimatedBytes(tiles: Long) = tiles * AVG_TILE_BYTES

    fun fits(b: Bounds) = tiles(b).let { it <= MAX_TILES && estimatedBytes(it) <= MAX_BYTES }
}
