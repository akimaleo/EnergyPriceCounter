package com.kawa.energy.counter.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Semicircular speedometer-style gauge.
 *
 * @param value current value
 * @param min lower bound (degrees on the dial)
 * @param max upper bound
 * @param unit unit suffix shown under the value
 * @param label caption shown at the bottom
 * @param sizeDp diameter of the gauge
 */
@Composable
fun SpeedometerGauge(
    value: Double,
    min: Double,
    max: Double,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 220,
    animationMs: Int = 800,
) {
    val safeMin = min
    val safeMax = if (max > min) max else min + 1.0
    val clamped = value.coerceIn(safeMin, safeMax)
    val targetFraction = ((clamped - safeMin) / (safeMax - safeMin)).toFloat().coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = animationMs, easing = FastOutSlowInEasing),
        label = "gaugeFraction",
    )
    val animatedValue by animateFloatAsState(
        targetValue = clamped.toFloat(),
        animationSpec = tween(durationMillis = animationMs, easing = FastOutSlowInEasing),
        label = "gaugeValue",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline
    val needleColor = MaterialTheme.colorScheme.onSurface
    val low = MaterialTheme.colorScheme.primary
    val mid = MaterialTheme.colorScheme.tertiary
    val high = MaterialTheme.colorScheme.error
    val fraction = animatedFraction
    val arcColor = if (fraction < 0.5f) lerp(low, mid, fraction * 2f)
        else lerp(mid, high, (fraction - 0.5f) * 2f)

    Box(modifier = modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = size.minDimension * 0.10f
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset(
                (size.width - arcSize.width) / 2f,
                (size.height - arcSize.height) / 2f + size.height * 0.10f,
            )
            // 270° sweep starting at 135° (left-down) going clockwise.
            val startAngle = 135f
            val sweep = 270f

            // Background track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Foreground arc up to fraction
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = sweep * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Tick marks (11 ticks)
            val center = Offset(topLeft.x + arcSize.width / 2f, topLeft.y + arcSize.height / 2f)
            val rOuter = arcSize.minDimension / 2f
            val rInnerMajor = rOuter - stroke * 0.9f
            val rInnerMinor = rOuter - stroke * 0.4f
            repeat(11) { i ->
                val isMajor = i % 2 == 0
                val angleDeg = startAngle + sweep * (i / 10f)
                val rad = angleDeg.toDouble() * PI / 180.0
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                val rInner = if (isMajor) rInnerMajor else rInnerMinor
                drawLine(
                    color = tickColor,
                    start = Offset(center.x + cosA * rInner, center.y + sinA * rInner),
                    end = Offset(center.x + cosA * rOuter, center.y + sinA * rOuter),
                    strokeWidth = if (isMajor) 2.5f else 1.5f,
                )
            }

            // Needle
            val needleAngleDeg = startAngle + sweep * fraction
            val needleRad = needleAngleDeg.toDouble() * PI / 180.0
            val needleLen = rOuter - stroke * 0.5f
            drawLine(
                color = needleColor,
                start = center,
                end = Offset(
                    center.x + cos(needleRad).toFloat() * needleLen,
                    center.y + sin(needleRad).toFloat() * needleLen,
                ),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = needleColor, radius = stroke * 0.35f, center = center)
            drawCircle(color = trackColor, radius = stroke * 0.18f, center = center)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = sizeDp.dp / 2 + 4.dp),
        ) {
            Text(
                formatValue(animatedValue.toDouble()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(unit, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = tickColor)
        }
    }
}

private fun formatValue(v: Double): String {
    val factor = 100.0
    val r = kotlin.math.round(v * factor) / factor
    return r.toString()
}
