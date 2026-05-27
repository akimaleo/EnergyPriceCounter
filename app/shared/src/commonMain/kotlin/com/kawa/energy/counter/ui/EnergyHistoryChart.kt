package com.kawa.energy.counter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kawa.energy.counter.history.SessionSample

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
        val currency = samples.first().currency

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendDot(kwhColor, "kWh consumed")
            LegendDot(costColor, "Cumulative cost ($currency)")
        }

        Spacer(Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            val tMin = samples.first().timestampMs.toDouble()
            val tMax = samples.last().timestampMs.toDouble()
            val tRange = (tMax - tMin).coerceAtLeast(1.0)

            val kwhMax = samples.maxOf { it.kwh }.coerceAtLeast(1e-9)
            val costMax = samples.maxOf { it.costInCurrency }.coerceAtLeast(1e-9)

            val w = size.width
            val h = size.height

            // Background gridlines (4 horizontal).
            for (i in 0..4) {
                val y = h * i / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                )
            }

            fun xFor(t: Long) = (((t - tMin) / tRange) * w).toFloat()
            fun yForKwh(v: Double) = (h - (v / kwhMax) * h).toFloat()
            fun yForCost(v: Double) = (h - (v / costMax) * h).toFloat()

            // Split samples into contiguous segments wherever the gap between
            // consecutive timestamps exceeds the inactivity threshold (~3x the
            // sampling interval). This produces visual gaps for paused sessions.
            val gapThresholdMs = INACTIVITY_GAP_MS
            val segments = splitIntoSegments(samples, gapThresholdMs)

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
                drawPath(kwhPath, color = kwhColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
                drawPath(costPath, color = costColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

                // Draw a small dot at segment boundaries so isolated samples are still visible.
                if (segment.size == 1) {
                    val s = segment[0]
                    val x = xFor(s.timestampMs)
                    drawCircle(kwhColor, radius = 3f, center = Offset(x, yForKwh(s.kwh)))
                    drawCircle(costColor, radius = 3f, center = Offset(x, yForCost(s.costInCurrency)))
                }
            }
        }

        AxisLabels(samples)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(5.dp)),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AxisLabels(samples: List<SessionSample>) {
    val first = samples.first()
    val last = samples.last()
    val maxKwh = samples.maxOf { it.kwh }
    val maxCost = samples.maxOf { it.costInCurrency }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "kWh: 0 → ${kwhFmt(maxKwh)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${first.currency}: 0 → ${costFmt(maxCost)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "span ${humanDuration(last.timestampMs - first.timestampMs)} · ${samples.size} samples",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun kwhFmt(v: Double): String {
    val factor = 1000000.0
    val rounded = kotlin.math.round(v * factor) / factor
    return rounded.toString()
}
private fun costFmt(v: Double): String {
    val factor = 10000.0
    val rounded = kotlin.math.round(v * factor) / factor
    return rounded.toString()
}
private fun humanDuration(ms: Long): String = when {
    ms < 60_000 -> "${ms / 1000}s"
    ms < 3_600_000 -> "${ms / 60_000}m ${(ms / 1000) % 60}s"
    else -> "${ms / 3_600_000}h ${(ms / 60_000) % 60}m"
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
