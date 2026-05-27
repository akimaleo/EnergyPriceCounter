package com.kawa.energy.counter.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kawa.energy.counter.domain.SessionSample
import kotlin.math.max

@Composable
fun EnergyHistoryChart(
    samples: List<SessionSample>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (samples.size < 2) {
            Text(
                "Not enough data yet. Run a session for a few seconds to build the chart.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        val kwhColor = MaterialTheme.colorScheme.primary
        val costColor = MaterialTheme.colorScheme.error
        val gridColor = MaterialTheme.colorScheme.outlineVariant
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val currency = samples.first().currency

        val stats = remember(samples) { computeStats(samples) }

        // Compact summary strip above the chart.
        SummaryStrip(stats = stats, currency = currency)

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendDot(kwhColor, "kWh consumed")
            LegendDot(costColor, "Cumulative cost ($currency)")
        }

        Spacer(Modifier.height(6.dp))

        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val labelStyleLeft = TextStyle(
            color = kwhColor,
            fontSize = 9.sp,
        )
        val labelStyleRight = TextStyle(
            color = costColor,
            fontSize = 9.sp,
            textAlign = TextAlign.End,
        )
        val labelStyleAxis = TextStyle(
            color = labelColor,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        ) {
            // Plot area inset: reserve space for Y-axis labels (left, right) and
            // X-axis labels (bottom).
            val leftAxisPx = with(density) { 44.dp.toPx() }
            val rightAxisPx = with(density) { 52.dp.toPx() }
            val bottomAxisPx = with(density) { 18.dp.toPx() }
            val plotLeft = leftAxisPx
            val plotRight = size.width - rightAxisPx
            val plotTop = 0f
            val plotBottom = size.height - bottomAxisPx
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - plotTop

            val tMin = samples.first().timestampMs.toDouble()
            val tMax = samples.last().timestampMs.toDouble()
            val tRange = (tMax - tMin).coerceAtLeast(1.0)

            val kwhMax = samples.maxOf { it.kwh }.coerceAtLeast(1e-9)
            val costMax = samples.maxOf { it.costInCurrency }.coerceAtLeast(1e-9)

            fun xFor(t: Long) = plotLeft + (((t - tMin) / tRange) * plotWidth).toFloat()
            fun yForKwh(v: Double) = plotBottom - ((v / kwhMax) * plotHeight).toFloat()
            fun yForCost(v: Double) = plotBottom - ((v / costMax) * plotHeight).toFloat()

            // Horizontal gridlines + Y-axis labels (4 segments → 5 tick marks)
            val tickCount = 4
            for (i in 0..tickCount) {
                val frac = i.toFloat() / tickCount
                val y = plotBottom - frac * plotHeight
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f,
                )
                val kwhValue = kwhMax * frac
                val costValue = costMax * frac
                val leftLayout = measurer.measure(formatKwh(kwhValue), labelStyleLeft)
                val rightLayout = measurer.measure(formatCost(costValue), labelStyleRight)
                drawText(
                    textLayoutResult = leftLayout,
                    topLeft = Offset(plotLeft - leftLayout.size.width - 4.dp.toPx(), y - leftLayout.size.height / 2f),
                )
                drawText(
                    textLayoutResult = rightLayout,
                    topLeft = Offset(plotRight + 4.dp.toPx(), y - rightLayout.size.height / 2f),
                )
            }

            // Vertical gridlines + X-axis labels (start, mid, end relative offsets)
            val xTicks = 3
            for (i in 0 until xTicks) {
                val frac = i.toFloat() / (xTicks - 1)
                val x = plotLeft + frac * plotWidth
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
                val tAt = (tMin + frac * tRange).toLong()
                val offsetFromEnd = samples.last().timestampMs - tAt
                val label = humanOffset(offsetFromEnd)
                val layout = measurer.measure(label, labelStyleAxis)
                val tx = (x - layout.size.width / 2f).coerceIn(plotLeft - 8f, plotRight - layout.size.width + 8f)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(tx, plotBottom + 4.dp.toPx()),
                )
            }

            // Series — render in segments so paused sessions get visual gaps.
            val segments = splitIntoSegments(samples, INACTIVITY_GAP_MS)
            segments.forEach { segment ->
                val kwhPath = Path().apply {
                    segment.forEachIndexed { i, s ->
                        val x = xFor(s.timestampMs)
                        val y = yForKwh(s.kwh)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                val costPath = Path().apply {
                    segment.forEachIndexed { i, s ->
                        val x = xFor(s.timestampMs)
                        val y = yForCost(s.costInCurrency)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(kwhPath, color = kwhColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                drawPath(costPath, color = costColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

                if (segment.size == 1) {
                    val s = segment[0]
                    val x = xFor(s.timestampMs)
                    drawCircle(kwhColor, radius = 3f, center = Offset(x, yForKwh(s.kwh)))
                    drawCircle(costColor, radius = 3f, center = Offset(x, yForCost(s.costInCurrency)))
                }
            }

            // Highlight the latest sample with filled+ringed dots on both series.
            val last = samples.last()
            drawEndpointMarker(Offset(xFor(last.timestampMs), yForKwh(last.kwh)), kwhColor)
            drawEndpointMarker(Offset(xFor(last.timestampMs), yForCost(last.costInCurrency)), costColor)
        }

        Spacer(Modifier.height(4.dp))
        FooterRow(stats = stats, currency = currency)
    }
}

private fun DrawScope.drawEndpointMarker(center: Offset, color: Color) {
    drawCircle(color.copy(alpha = 0.25f), radius = 7f, center = center)
    drawCircle(color, radius = 3.5f, center = center)
}

@Composable
private fun SummaryStrip(stats: ChartStats, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryItem(
            value = formatKwh(stats.totalKwh),
            unit = "kWh",
            label = "Total",
            accent = MaterialTheme.colorScheme.primary,
        )
        SummaryItem(
            value = formatCost(stats.totalCost),
            unit = currency,
            label = "Cost",
            accent = MaterialTheme.colorScheme.error,
        )
        SummaryItem(
            value = formatWatts(stats.avgWatts),
            unit = "W avg",
            label = "Power",
            accent = MaterialTheme.colorScheme.tertiary,
        )
        SummaryItem(
            value = stats.sessions.toString(),
            unit = if (stats.sessions == 1) "session" else "sessions",
            label = "Active",
            accent = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun SummaryItem(value: String, unit: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(5.dp)),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FooterRow(stats: ChartStats, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "span ${humanDuration(stats.spanMs)} · ${stats.sampleCount} samples",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "rate ≈ ${formatCost(stats.costPerHour)} $currency / h",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Stats ------------------------------------------------------------------

private data class ChartStats(
    val totalKwh: Double,
    val totalCost: Double,
    val sessions: Int,
    val avgWatts: Double,
    val sampleCount: Int,
    val spanMs: Long,
    val costPerHour: Double,
)

private fun computeStats(samples: List<SessionSample>): ChartStats {
    if (samples.isEmpty()) {
        return ChartStats(0.0, 0.0, 0, 0.0, 0, 0L, 0.0)
    }
    val segments = splitIntoSegments(samples, INACTIVITY_GAP_MS)
    var totalKwh = 0.0
    var totalCost = 0.0
    var totalActiveMs = 0L
    segments.forEach { seg ->
        if (seg.isEmpty()) return@forEach
        totalKwh += max(0.0, seg.last().kwh - seg.first().kwh)
        totalCost += max(0.0, seg.last().costInCurrency - seg.first().costInCurrency)
        totalActiveMs += (seg.last().timestampMs - seg.first().timestampMs).coerceAtLeast(0L)
    }
    val avgWatts = if (totalActiveMs > 0) totalKwh * 1000.0 * 3_600_000.0 / totalActiveMs else 0.0
    val spanMs = samples.last().timestampMs - samples.first().timestampMs
    val costPerHour = if (totalActiveMs > 0) totalCost * 3_600_000.0 / totalActiveMs else 0.0
    return ChartStats(
        totalKwh = totalKwh,
        totalCost = totalCost,
        sessions = segments.count { it.isNotEmpty() },
        avgWatts = avgWatts,
        sampleCount = samples.size,
        spanMs = spanMs,
        costPerHour = costPerHour,
    )
}

// --- Formatters -------------------------------------------------------------

private fun formatKwh(v: Double): String = roundTo(v, 4)
private fun formatCost(v: Double): String = roundTo(v, 4)
private fun formatWatts(v: Double): String = roundTo(v, 1)

private fun roundTo(value: Double, digits: Int): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    val factor = generateSequence(1.0) { it * 10.0 }.elementAt(digits)
    val rounded = kotlin.math.round(value * factor) / factor
    val whole = rounded.toLong()
    val fraction = kotlin.math.abs(rounded - whole)
    val fracStr = ((fraction * factor).toLong()).toString().padStart(digits, '0')
    val sign = if (rounded < 0 && whole == 0L) "-" else ""
    return if (digits == 0) "$sign$whole" else "$sign$whole.$fracStr"
}

private fun humanDuration(ms: Long): String = when {
    ms <= 0 -> "0s"
    ms < 60_000 -> "${ms / 1000}s"
    ms < 3_600_000 -> "${ms / 60_000}m ${(ms / 1000) % 60}s"
    else -> "${ms / 3_600_000}h ${(ms / 60_000) % 60}m"
}

/** "now", "−12m", "−2h 05m" — used for x-axis labels relative to the latest sample. */
private fun humanOffset(msAgo: Long): String = when {
    msAgo <= 5_000 -> "now"
    msAgo < 60_000 -> "−${msAgo / 1000}s"
    msAgo < 3_600_000 -> "−${msAgo / 60_000}m"
    else -> {
        val h = msAgo / 3_600_000
        val m = (msAgo / 60_000) % 60
        if (m == 0L) "−${h}h" else "−${h}h ${m.toString().padStart(2, '0')}m"
    }
}

// Samples are persisted every ~5s while a session is active. A gap > 15s implies
// the session was stopped, so we render the chart as discrete segments.
private const val INACTIVITY_GAP_MS = 15_000L

private fun splitIntoSegments(
    samples: List<SessionSample>,
    gapThresholdMs: Long,
): List<List<SessionSample>> {
    if (samples.isEmpty()) return emptyList()
    val out = mutableListOf<MutableList<SessionSample>>()
    var current = mutableListOf<SessionSample>().also { out += it }
    samples.forEachIndexed { i, s ->
        if (i == 0) {
            current += s
        } else {
            val gap = s.timestampMs - samples[i - 1].timestampMs
            if (gap > gapThresholdMs) {
                current = mutableListOf<SessionSample>().also { out += it }
            }
            current += s
        }
    }
    return out
}
