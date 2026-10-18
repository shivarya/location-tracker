package com.shivarya.locationspeedtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** One plottable series: x is time or distance (any monotonic unit), y in display units. */
data class Series(val xs: DoubleArray, val ys: DoubleArray) {
    val size get() = xs.size

    /** Index of the sample nearest to [x] (binary search; xs ascending). */
    fun nearest(x: Double): Int {
        if (size == 0) return -1
        var lo = 0
        var hi = size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (xs[mid] < x) lo = mid + 1 else hi = mid
        }
        return if (lo > 0 && x - xs[lo - 1] < xs[lo] - x) lo - 1 else lo
    }

    /** At most [n] points, keeping each bucket's min and max so peaks survive. */
    fun downsample(n: Int): Series {
        if (size <= n || n < 4) return this
        val bucket = size.toDouble() / (n / 2)
        val ox = ArrayList<Double>(n)
        val oy = ArrayList<Double>(n)
        var i = 0.0
        while (i < size) {
            val a = i.toInt()
            val b = min(size, (i + bucket).toInt()).coerceAtLeast(a + 1)
            var minI = a
            var maxI = a
            for (k in a until b) {
                if (ys[k] < ys[minI]) minI = k
                if (ys[k] > ys[maxI]) maxI = k
            }
            val (first, second) = if (minI <= maxI) minI to maxI else maxI to minI
            ox += xs[first]; oy += ys[first]
            if (second != first) { ox += xs[second]; oy += ys[second] }
            i += bucket
        }
        return Series(ox.toDoubleArray(), oy.toDoubleArray())
    }

    override fun equals(other: Any?) = other is Series && xs.contentEquals(other.xs) && ys.contentEquals(other.ys)
    override fun hashCode() = 31 * xs.contentHashCode() + ys.contentHashCode()
}

/**
 * A compact line/area chart with a scrub cursor. [cursorX] draws the shared cursor (several charts scrub together);
 * [onScrub] reports the x under the finger (null when released).
 */
@Composable
fun LineChart(
    title: String,
    series: Series,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    cursorX: Double? = null,
    onScrub: (Double?) -> Unit = {},
    formatY: (Double) -> String = { "%.0f".format(it) },
    zeroBased: Boolean = false,
    /** Smallest y span to draw, so flat-ish data (0.01 g of sensor noise) isn't blown up to fill the chart. */
    minRange: Double = 0.0,
) {
    val data = remember(series) { series.downsample(600) }
    if (data.size < 2) return
    val minY = if (zeroBased) min(0.0, data.ys.min()) else data.ys.min()
    val maxY = data.ys.max().let { if (it - minY < maxOf(minRange, 1e-6)) minY + maxOf(minRange, 1.0) else it }
    val x0 = data.xs.first()
    val x1 = data.xs.last().let { if (it - x0 < 1e-9) x0 + 1 else it }
    val cursorIndex = cursorX?.let { series.nearest(it) }?.takeIf { it >= 0 }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier.semantics { contentDescription = "$title chart" }) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            val label = if (cursorIndex != null) "${formatY(series.ys[cursorIndex])} $unit" else "max ${formatY(data.ys.max())} $unit"
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .pointerInput(x0, x1) {
                        detectTapGestures(
                            onPress = { o ->
                                onScrub(x0 + (o.x / size.width).coerceIn(0f, 1f) * (x1 - x0))
                                tryAwaitRelease()
                                onScrub(null)
                            },
                        )
                    }
                    .pointerInput(x0, x1) {
                        detectDragGestures(
                            onDragEnd = { onScrub(null) },
                            onDragCancel = { onScrub(null) },
                        ) { change, _ ->
                            onScrub(x0 + (change.position.x / size.width).coerceIn(0f, 1f) * (x1 - x0))
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                fun px(x: Double) = ((x - x0) / (x1 - x0) * w).toFloat()
                fun py(y: Double) = (h - (y - minY) / (maxY - minY) * h * 0.92f - h * 0.04f).toFloat()
                for (i in 0..2) {
                    val y = h * i / 2f
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
                val line = Path()
                val area = Path()
                for (i in 0 until data.size) {
                    val x = px(data.xs[i])
                    val y = py(data.ys[i])
                    if (i == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, h)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(px(data.xs.last()), h)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
                drawPath(line, color, style = Stroke(width = 2.5.dp.toPx()))
                if (cursorIndex != null) {
                    val cx = px(series.xs[cursorIndex])
                    val cy = py(series.ys[cursorIndex])
                    drawLine(onSurface.copy(alpha = 0.6f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5f)
                    drawCircle(color, radius = 5.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }
        Row {
            Text(formatY(minY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatY(maxY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun seriesOf(xs: List<Double>, ys: List<Double>) = Series(xs.toDoubleArray(), ys.toDoubleArray())
