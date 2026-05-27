package com.kawa.energy.counter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kawa.energy.counter.domain.SessionSample

/**
 * Tiny line chart for use inside dense headers / list rows.
 * Renders nothing when there are fewer than 2 points.
 *
 * @param samples timestamped samples; X is timestamp, Y is the result of [select]
 * @param select extracts the value to plot
 * @param color stroke colour
 * @param strokeWidth in pixels
 * @param inactivityGapMs samples whose neighbours are further apart than this
 *   are rendered as separate segments (visual gaps for paused sessions).
 */
@Composable
fun Sparkline(
    samples: List<SessionSample>,
    select: (SessionSample) -> Double,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    inactivityGapMs: Long = 15_000L,
) {
    if (samples.size < 2) return
    Canvas(modifier = modifier) {
        val tMin = samples.first().timestampMs.toDouble()
        val tMax = samples.last().timestampMs.toDouble()
        val tRange = (tMax - tMin).coerceAtLeast(1.0)

        val values = samples.map(select)
        val vMin = values.min()
        val vMax = values.max()
        val vRange = (vMax - vMin).coerceAtLeast(1e-9)

        val w = size.width
        val h = size.height

        fun xFor(t: Long) = (((t - tMin) / tRange) * w).toFloat()
        fun yFor(v: Double) = (h - ((v - vMin) / vRange) * h).toFloat()

        var pathOpen = false
        val path = Path()
        samples.forEachIndexed { i, s ->
            val gap = if (i == 0) 0L else s.timestampMs - samples[i - 1].timestampMs
            if (i == 0 || gap > inactivityGapMs) {
                if (pathOpen) {
                    drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    path.reset()
                }
                path.moveTo(xFor(s.timestampMs), yFor(select(s)))
                pathOpen = true
            } else {
                path.lineTo(xFor(s.timestampMs), yFor(select(s)))
            }
        }
        if (pathOpen) {
            drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        // Mark the latest point with a small dot.
        val last = samples.last()
        drawCircle(color = color, radius = strokeWidth + 0.5f, center = Offset(xFor(last.timestampMs), yFor(select(last))))
    }
}
