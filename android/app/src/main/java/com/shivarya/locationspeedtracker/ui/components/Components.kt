package com.shivarya.locationspeedtracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import kotlin.math.cos
import kotlin.math.sin

private const val GAUGE_START = 135f
private const val GAUGE_SWEEP = 270f

/**
 * Speedometer: a 270° dial with ticks, a progress arc that animates to [value], and the big digital readout.
 * [value]/[max] are in display units. [primary] is the big number's text (speed, or pace for foot activities).
 */
@Composable
fun SpeedGauge(
    value: Double,
    max: Double,
    primary: String,
    unit: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    secondary: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val fraction by animateFloatAsState(
        targetValue = if (max > 0) (value / max).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(600),
        label = "gauge",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val hot = SpeedTrackTheme.signal.bad
    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "Speed $primary $unit" },
        contentAlignment = Alignment.Center,
    ) {
        val side = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.07f
            val inset = stroke / 2 + size.minDimension * 0.06f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, GAUGE_START, GAUGE_SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0.002f) {
                // Turns red over the last fifth of the dial.
                drawArc(
                    color = androidx.compose.ui.graphics.lerp(accent, hot, ((fraction - 0.8f) / 0.2f).coerceIn(0f, 1f)),
                    startAngle = GAUGE_START,
                    sweepAngle = GAUGE_SWEEP * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            // 10 major ticks just inside the arc.
            val r = arcSize.width / 2 - stroke * 1.1f
            for (i in 0..10) {
                val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * i / 10).toDouble())
                val len = if (i % 5 == 0) stroke * 0.9f else stroke * 0.5f
                val start = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
                val end = Offset(center.x + ((r - len) * cos(angle)).toFloat(), center.y + ((r - len) * sin(angle)).toFloat())
                drawLine(tick.copy(alpha = 0.6f), start, end, strokeWidth = stroke * 0.12f, cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                primary,
                fontSize = (side.value * 0.24f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (secondary != null) {
                Spacer(Modifier.height(4.dp))
                Text(secondary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    emphasis: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = if (emphasis) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/** A grid of [StatTile]s, [columns] across. */
@Composable
fun StatGrid(items: List<StatItem>, columns: Int, modifier: Modifier = Modifier, spacing: Dp = 8.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { StatTile(it.label, it.value, Modifier.weight(1f), unit = it.unit, icon = it.icon) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

data class StatItem(val label: String, val value: String, val unit: String? = null, val icon: ImageVector? = null)

/** GPS signal bars, thresholds from the Expo app's GPSStrengthIndicator. */
enum class GpsQuality(val label: String, val bars: Int) {
    Excellent("Excellent", 4), Good("Good", 3), Fair("Fair", 2), Poor("Poor", 1), Weak("Weak", 0), None("No fix", 0);

    companion object {
        fun of(accuracyM: Double?): GpsQuality = when {
            accuracyM == null -> None
            accuracyM <= 5 -> Excellent
            accuracyM <= 10 -> Good
            accuracyM <= 20 -> Fair
            accuracyM <= 50 -> Poor
            else -> Weak
        }
    }
}

@Composable
fun GpsQuality.color(): Color {
    val s = SpeedTrackTheme.signal
    return when (this) {
        GpsQuality.Excellent -> s.excellent
        GpsQuality.Good -> s.good
        GpsQuality.Fair -> s.fair
        GpsQuality.Poor -> s.poor
        GpsQuality.Weak, GpsQuality.None -> s.bad
    }
}

@Composable
fun GpsChip(accuracyM: Double?, modifier: Modifier = Modifier, satellites: Int? = null, onClick: (() -> Unit)? = null) {
    val q = GpsQuality.of(accuracyM)
    val color = q.color()
    Surface(
        modifier = modifier.semantics { contentDescription = "GPS ${q.label}" },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SignalBars(q.bars, color)
            Spacer(Modifier.width(6.dp))
            val text = buildString {
                append(if (accuracyM != null) "±${accuracyM.toInt()} m" else q.label)
                if (satellites != null && satellites > 0) append(" · $satellites sat")
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SignalBars(bars: Int, color: Color, modifier: Modifier = Modifier) {
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(width = 18.dp, height = 14.dp)) {
        val w = size.width / 4
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.65f * (i + 1) / 4)
            drawRoundRect(
                color = if (i < bars) color else off,
                topLeft = Offset(i * w + w * 0.15f, size.height - h),
                size = Size(w * 0.7f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.2f),
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(contentPadding)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/** A tinted call-to-action banner (permission needed, location off, offline…). */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.tertiary,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.14f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (action != null) {
                    Spacer(Modifier.height(8.dp))
                    action()
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Blue → green → yellow → red ramp for speed-coloured routes. [t] in 0..1. */
fun speedRamp(t: Double): Color {
    val stops = listOf(
        0.0 to Color(0xFF2E86FF),
        0.35 to Color(0xFF00D4A0),
        0.65 to Color(0xFFF1C40F),
        1.0 to Color(0xFFE74C3C),
    )
    val x = t.coerceIn(0.0, 1.0)
    for (i in 1 until stops.size) {
        val (p1, c1) = stops[i - 1]
        val (p2, c2) = stops[i]
        if (x <= p2) {
            val f = ((x - p1) / (p2 - p1)).toFloat()
            return androidx.compose.ui.graphics.lerp(c1, c2, f)
        }
    }
    return stops.last().second
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun FullWidthBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}
