package com.kawa.energy.counter.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Smoothly tweens between successive Double values. Useful for counters whose
 * underlying value updates abruptly each sample.
 */
@Composable
fun AnimatedNumber(
    value: Double,
    digits: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    fontWeight: FontWeight = FontWeight.SemiBold,
    animationMs: Int = 600,
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = animationMs, easing = FastOutSlowInEasing),
        label = "animatedNumber",
    )
    Text(
        text = format(animated.toDouble(), digits),
        style = style,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}

private fun format(value: Double, digits: Int): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    val factor = generateSequence(1.0) { it * 10.0 }.elementAt(digits)
    val rounded = kotlin.math.round(value * factor) / factor
    val whole = rounded.toLong()
    val fraction = kotlin.math.abs(rounded - whole)
    val fracStr = ((fraction * factor).toLong()).toString().padStart(digits, '0')
    val sign = if (rounded < 0 && whole == 0L) "-" else ""
    return if (digits == 0) "$sign$whole" else "$sign$whole.$fracStr"
}
