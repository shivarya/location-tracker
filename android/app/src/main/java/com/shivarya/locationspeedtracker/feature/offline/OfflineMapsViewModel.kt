package com.shivarya.locationspeedtracker.feature.offline

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.annotation.SuppressLint
import com.google.android.gms.location.LocationServices
import com.shivarya.locationspeedtracker.core.geo.Bounds
import com.shivarya.locationspeedtracker.core.geo.LatLon
import com.shivarya.locationspeedtracker.core.location.LocationPermissions
import kotlinx.coroutines.tasks.await
import com.shivarya.locationspeedtracker.core.location.NetworkMonitor
import com.shivarya.locationspeedtracker.core.maps.MapDetail
import com.shivarya.locationspeedtracker.core.maps.OfflineArea
import com.shivarya.locationspeedtracker.core.maps.OfflineRegions
import com.shivarya.locationspeedtracker.core.maps.TileEstimator
import com.shivarya.locationspeedtracker.core.tracking.TrackingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

data class Selection(
    val bounds: Bounds,
    val tiles: Long,
    val bytes: Long,
    val fits: Boolean,
    /** When it doesn't fit: the most detailed lower level that would, if any. */
    val fitsAt: MapDetail?,
)

data class OfflineUi(
    val areas: List<OfflineArea> = emptyList(),
    val selection: Selection? = null,
    val detail: MapDetail = MapDetail.Streets,
    val suggestedName: String = "",
    val online: Boolean = true,
) {
    val totalBytes get() = areas.sumOf { it.tileBytes } + (areas.maxOfOrNull { it.sharedBytes } ?: 0)
}

@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val regions: OfflineRegions,
    network: NetworkMonitor,
    engine: TrackingEngine,
) : ViewModel() {
    private val selection = MutableStateFlow<Selection?>(null)
    private val detail = MutableStateFlow(MapDetail.Streets)
    private var bounds: Bounds? = null
    private val name = MutableStateFlow("")
    /** Where the picker opens: the recorder's fix, else the last known location. */
    private val _start = MutableStateFlow<LatLon?>(engine.state.value.fix?.let { LatLon(it.lat, it.lon) })
    val start: StateFlow<LatLon?> = _start

    val ui: StateFlow<OfflineUi> = combine(regions.areas, selection, detail, name, network.online) { a, s, d, n, online ->
        OfflineUi(a, s, d, n, online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineUi())

    init {
        regions.load()
        if (_start.value == null && LocationPermissions.hasAny(context)) {
            viewModelScope.launch {
                @SuppressLint("MissingPermission")
                val last = runCatching { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }.getOrNull()
                if (last != null && _start.value == null) _start.value = LatLon(last.latitude, last.longitude)
            }
        }
    }

    fun onBounds(b: Bounds) {
        bounds = b
        select(b)
        viewModelScope.launch { name.value = placeName(b) ?: defaultName() }
    }

    fun setDetail(d: MapDetail) {
        detail.value = d
        bounds?.let(::select)
    }

    private fun select(b: Bounds) {
        val z = detail.value.maxZoom
        val fits = TileEstimator.fits(b, z)
        val fitsAt = if (fits) null
        else MapDetail.entries.filter { it.maxZoom < z && TileEstimator.fits(b, it.maxZoom) }.maxByOrNull { it.maxZoom }
        selection.value = Selection(b, TileEstimator.tiles(b, 0, z), TileEstimator.estimatedBytes(b, z), fits, fitsAt)
    }

    fun download(label: String) {
        val s = selection.value ?: return
        if (!s.fits) return
        regions.download(label.ifBlank { defaultName() }, s.bounds, detail.value)
    }

    fun pause(a: OfflineArea) = regions.pause(a.group)
    fun resume(a: OfflineArea) = regions.resume(a.group)
    fun delete(a: OfflineArea) = regions.delete(a.group)

    private fun defaultName() = "Area · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())}"

    /** Locality name for the area's centre (needs a connection; null offline or on failure). */
    private suspend fun placeName(b: Bounds): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val g = Geocoder(context, Locale.getDefault())
        val c = b.center
        withTimeoutOrNull(4_000) {
            runCatching {
                val address = if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        g.getFromLocation(c.lat, c.lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) = cont.resume(addresses.firstOrNull())
                            override fun onError(errorMessage: String?) = cont.resume(null)
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    g.getFromLocation(c.lat, c.lon, 1)?.firstOrNull()
                }
                address?.let { it.subLocality ?: it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()
        }
    }
}
