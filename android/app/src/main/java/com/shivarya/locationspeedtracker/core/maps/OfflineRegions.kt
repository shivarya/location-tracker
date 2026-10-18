package com.shivarya.locationspeedtracker.core.maps

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shivarya.locationspeedtracker.core.geo.Bounds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Stored as each MapLibre region's metadata. An area is a light + dark region pair (same tiles, two styles). */
@Serializable
data class AreaMeta(
    val group: String,
    val name: String,
    val dark: Boolean,
    val created: Long,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

data class OfflineArea(
    val group: String,
    val name: String,
    val bounds: Bounds,
    val createdAt: Long,
    val completedBytes: Long,
    val completedResources: Long,
    val requiredResources: Long,
    val complete: Boolean,
    val downloading: Boolean,
    val error: String?,
) {
    val progress: Float get() = if (requiredResources > 0) (completedResources.toFloat() / requiredResources).coerceIn(0f, 1f) else 0f
}

/**
 * Downloadable OpenFreeMap areas for offline use (MapLibre's offline database). The light style downloads first;
 * the dark one follows and reuses the cached tiles, so night mode works offline without fetching tiles twice.
 * All MapLibre offline callbacks arrive on the main thread; every method here must be called on it too.
 */
@Singleton
class OfflineRegions @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val main = Handler(Looper.getMainLooper())
    private val manager: OfflineManager by lazy {
        MapLibreSetup.ensure(context)
        OfflineManager.getInstance(context)
    }

    private class Tracked(val region: OfflineRegion, val meta: AreaMeta, var status: OfflineRegionStatus? = null, var active: Boolean = false, var error: String? = null)

    private val tracked = LinkedHashMap<Long, Tracked>()
    private val _areas = MutableStateFlow<List<OfflineArea>>(emptyList())
    val areas: StateFlow<List<OfflineArea>> = _areas.asStateFlow()
    private var loaded = false

    fun load() = onMain {
        if (loaded) return@onMain
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                loaded = true
                offlineRegions.orEmpty().forEach { r -> meta(r)?.let { track(r, it) } }
                publish()
            }

            override fun onError(error: String) {
                Log.w(TAG, "listOfflineRegions: $error")
            }
        })
    }

    fun download(name: String, bounds: Bounds) = onMain {
        val group = UUID.randomUUID().toString()
        create(AreaMeta(group, name, dark = false, System.currentTimeMillis(), bounds.south, bounds.west, bounds.north, bounds.east), start = true)
    }

    private fun create(meta: AreaMeta, start: Boolean) {
        val b = LatLngBounds.Builder().include(LatLng(meta.south, meta.west)).include(LatLng(meta.north, meta.east)).build()
        val def = OfflineTilePyramidRegionDefinition(
            MapLibreSetup.style(meta.dark), b, 0.0, TileEstimator.MAX_ZOOM.toDouble(), context.resources.displayMetrics.density,
        )
        manager.createOfflineRegion(def, json.encodeToString(AreaMeta.serializer(), meta).toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                val t = track(offlineRegion, meta)
                if (start) setActive(t, true)
                publish()
            }

            override fun onError(error: String) {
                Log.w(TAG, "createOfflineRegion: $error")
            }
        })
    }

    fun pause(group: String) = onMain {
        tracked.values.filter { it.meta.group == group }.forEach { setActive(it, false) }
        publish()
    }

    fun resume(group: String) = onMain {
        // Resume the light region if unfinished, else the dark one.
        val parts = tracked.values.filter { it.meta.group == group }.sortedBy { it.meta.dark }
        parts.firstOrNull { it.status?.isComplete != true }?.let {
            it.error = null
            setActive(it, true)
        }
        publish()
    }

    fun delete(group: String) = onMain {
        tracked.values.filter { it.meta.group == group }.toList().forEach { t ->
            setActive(t, false)
            t.region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    tracked.remove(t.region.id)
                    publish()
                }

                override fun onError(error: String) {
                    Log.w(TAG, "delete: $error")
                }
            })
        }
    }

    private fun track(r: OfflineRegion, meta: AreaMeta): Tracked {
        val t = Tracked(r, meta)
        tracked[r.id] = t
        r.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                t.status = status
                when {
                    status.isComplete -> {
                        setActive(t, false)
                        if (!meta.dark) startDarkCompanion(meta)
                    }
                    status.completedResourceSize > TileEstimator.MAX_BYTES * 11 / 10 -> {
                        setActive(t, false)
                        t.error = "Stopped at the 50 MB per-area limit — choose a smaller area"
                    }
                }
                publish()
            }

            override fun onError(error: OfflineRegionError) {
                // Connection errors are retried by MapLibre; surface them so the user knows why it stalls.
                t.error = error.message
                publish()
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                setActive(t, false)
                t.error = "Tile limit reached ($limit tiles)"
                publish()
            }
        })
        r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                t.status = status
                publish()
            }

            override fun onError(error: String?) {}
        })
        return t
    }

    private fun startDarkCompanion(light: AreaMeta) {
        val existing = tracked.values.firstOrNull { it.meta.group == light.group && it.meta.dark }
        if (existing == null) create(light.copy(dark = true), start = true)
        else if (existing.status?.isComplete != true) setActive(existing, true)
    }

    private fun setActive(t: Tracked, active: Boolean) {
        t.active = active
        t.region.setDownloadState(if (active) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE)
    }

    private fun meta(r: OfflineRegion): AreaMeta? = runCatching {
        json.decodeFromString(AreaMeta.serializer(), String(r.metadata))
    }.getOrNull()

    private fun publish() {
        _areas.value = tracked.values.groupBy { it.meta.group }.map { (group, parts) ->
            val light = parts.firstOrNull { !it.meta.dark } ?: parts.first()
            val m = light.meta
            OfflineArea(
                group = group,
                name = m.name,
                bounds = Bounds(m.south, m.west, m.north, m.east),
                createdAt = m.created,
                completedBytes = parts.sumOf { it.status?.completedResourceSize ?: 0 },
                completedResources = parts.sumOf { it.status?.completedResourceCount ?: 0 },
                requiredResources = parts.sumOf { it.status?.requiredResourceCount ?: 0 },
                complete = parts.size >= 2 && parts.all { it.status?.isComplete == true },
                downloading = parts.any { it.active },
                error = parts.firstNotNullOfOrNull { it.error },
            )
        }.sortedByDescending { it.createdAt }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private companion object {
        const val TAG = "OfflineRegions"
    }
}
