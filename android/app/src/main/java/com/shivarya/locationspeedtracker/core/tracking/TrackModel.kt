package com.shivarya.locationspeedtracker.core.tracking

import com.shivarya.locationspeedtracker.core.geo.Bounds
import com.shivarya.locationspeedtracker.core.geo.Geo
import com.shivarya.locationspeedtracker.core.geo.LatLon
import com.shivarya.locationspeedtracker.core.geo.Simplifier
import com.shivarya.locationspeedtracker.core.model.ActivityType

/** A drawable route point: enough for a speed/elevation-coloured polyline and the chart scrubber. */
data class TrackSample(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Double,
    val altitude: Double?,
    val segment: Int,
    val distanceM: Double,
)

/**
 * An append-only list that is cheap to snapshot every second: full chunks are shared between snapshots and only
 * the short tail is copied. Map polylines draw one polyline per chunk, so finished chunks never re-render.
 */
class ChunkedTrack private constructor(
    val chunks: List<List<TrackSample>>,
    val tail: List<TrackSample>,
    /** Monotonic; differs whenever content differs (cheap equality for StateFlow). */
    val version: Long,
) {
    val size: Int get() = chunks.size * CHUNK + tail.size
    val last: TrackSample? get() = tail.lastOrNull() ?: chunks.lastOrNull()?.lastOrNull()

    fun append(s: TrackSample): ChunkedTrack {
        val newTail = tail + s
        return if (newTail.size >= CHUNK) ChunkedTrack(chunks + listOf(newTail), emptyList(), version + 1)
        else ChunkedTrack(chunks, newTail, version + 1)
    }

    /** All chunks including the tail, each prefixed with its predecessor's last point so lines join up. */
    fun drawableChunks(): List<List<TrackSample>> {
        val all = if (tail.isEmpty()) chunks else chunks + listOf(tail)
        return all.mapIndexed { i, c ->
            val prev = if (i > 0) all[i - 1].lastOrNull() else null
            if (prev != null && c.isNotEmpty() && prev.segment == c.first().segment) listOf(prev) + c else c
        }
    }

    fun toList(): List<TrackSample> = chunks.flatten() + tail

    override fun equals(other: Any?) = other is ChunkedTrack && other.version == version
    override fun hashCode() = version.hashCode()

    companion object {
        const val CHUNK = 200
        val Empty = ChunkedTrack(emptyList(), emptyList(), 0)

        fun of(samples: List<TrackSample>, version: Long = 1): ChunkedTrack {
            val full = samples.size / CHUNK * CHUNK
            return ChunkedTrack(samples.subList(0, full).chunked(CHUNK), samples.subList(full, samples.size).toList(), version)
        }
    }
}

/**
 * A finished route reduced to at most [maxPoints] for drawing (Douglas–Peucker, tolerance grown until it fits):
 * a 3 600-point polyline with a style span per segment stalls the Maps renderer for seconds.
 */
fun displayChunks(samples: List<TrackSample>, maxPoints: Int = 1200): List<List<TrackSample>> {
    if (samples.size <= maxPoints) return ChunkedTrack.of(samples).drawableChunks()
    val pts = samples.map { LatLon(it.lat, it.lon) }
    val b = Bounds.of(pts) ?: return emptyList()
    var tolerance = (Geo.distance(b.south, b.west, b.north, b.east) / 5000).coerceAtLeast(0.5)
    var keep = Simplifier.keepIndices(pts, tolerance)
    while (keep.size > maxPoints) {
        tolerance *= 1.6
        keep = Simplifier.keepIndices(pts, tolerance)
    }
    return ChunkedTrack.of(keep.map { samples[it] }).drawableChunks()
}

sealed interface TrackingStatus {
    data object Idle : TrackingStatus

    data class Active(
        val sessionId: String,
        val activity: ActivityType,
        val startTime: Long,
        val paused: Boolean,
        /** Total paused time before the current pause. */
        val pausedMs: Long,
        val pausedAt: Long?,
        val autoPaused: Boolean = false,
    ) : TrackingStatus {
        /** Active (non-paused) time at [now]. */
        fun elapsedMs(now: Long): Long {
            val pausedNow = if (paused && pausedAt != null) now - pausedAt else 0
            return (now - startTime - pausedMs - pausedNow).coerceAtLeast(0)
        }
    }
}

/** Everything the UI, notification and widget show live. */
data class LiveState(
    val status: TrackingStatus = TrackingStatus.Idle,
    /** Latest fix from the source, accepted by the filter or not. */
    val fix: Fix? = null,
    /** Speed to display: the filter's estimate while recording, Doppler otherwise. */
    val speedMps: Double? = null,
    val stats: SessionStats = SessionStats(),
    val track: ChunkedTrack = ChunkedTrack.Empty,
    val currentSplitIndex: Int = 0,
    val lastSplit: Split? = null,
    val laps: List<Split> = emptyList(),
    val error: String? = null,
) {
    val active get() = status as? TrackingStatus.Active
    val isRecording get() = active?.paused == false
}
