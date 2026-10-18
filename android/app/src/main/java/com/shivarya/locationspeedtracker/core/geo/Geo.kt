package com.shivarya.locationspeedtracker.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from point 1 to point 2, degrees 0..360. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}

data class LatLon(val lat: Double, val lon: Double)

/** A geographic rectangle (offline regions, camera fitting). */
data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val center get() = LatLon((south + north) / 2, (west + east) / 2)

    companion object {
        fun of(points: Iterable<LatLon>): Bounds? {
            var s = 90.0
            var w = 180.0
            var n = -90.0
            var e = -180.0
            var any = false
            for (p in points) {
                any = true
                if (p.lat < s) s = p.lat
                if (p.lat > n) n = p.lat
                if (p.lon < w) w = p.lon
                if (p.lon > e) e = p.lon
            }
            return if (any) Bounds(s, w, n, e) else null
        }
    }
}

/**
 * Douglas–Peucker simplification on an equirectangular projection — keeps long tracks drawable (map polylines,
 * charts) without visibly changing their shape. Returns the indices of points to keep, first and last always kept.
 */
object Simplifier {
    fun keepIndices(points: List<LatLon>, toleranceM: Double): IntArray {
        val n = points.size
        if (n <= 2) return IntArray(n) { it }
        val refLat = Math.toRadians(points[n / 2].lat)
        val kx = Geo.EARTH_RADIUS_M * cos(refLat) * Math.PI / 180
        val ky = Geo.EARTH_RADIUS_M * Math.PI / 180
        val xs = DoubleArray(n) { points[it].lon * kx }
        val ys = DoubleArray(n) { points[it].lat * ky }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, n - 1))
        val tol2 = toleranceM * toleranceM
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast().let { it[0] to it[1] }
            var maxD = 0.0
            var idx = -1
            val dx = xs[b] - xs[a]
            val dy = ys[b] - ys[a]
            val len2 = dx * dx + dy * dy
            for (i in a + 1 until b) {
                val d2 = if (len2 == 0.0) {
                    (xs[i] - xs[a]).let { it * it } + (ys[i] - ys[a]).let { it * it }
                } else {
                    val t = (((xs[i] - xs[a]) * dx + (ys[i] - ys[a]) * dy) / len2).coerceIn(0.0, 1.0)
                    val px = xs[a] + t * dx - xs[i]
                    val py = ys[a] + t * dy - ys[i]
                    px * px + py * py
                }
                if (d2 > maxD) {
                    maxD = d2
                    idx = i
                }
            }
            if (idx >= 0 && maxD > tol2) {
                keep[idx] = true
                stack.addLast(intArrayOf(a, idx))
                stack.addLast(intArrayOf(idx, b))
            }
        }
        return (0 until n).filter { keep[it] }.toIntArray()
    }
}
