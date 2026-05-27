package com.kawa.energy.counter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kawa.energy.counter.domain.SessionSample

@Composable
@Preview
fun SpeedometerGaugePreview() {
    PreviewSurface {
        SpeedometerGauge(
            value = 65.0,
            min = 0.0,
            max = 200.0,
            unit = "Watts",
            label = "Current power draw",
            sizeDp = 220,
        )
    }
}

@Composable
@Preview
fun SpeedometerGaugeHotPreview() {
    PreviewSurface {
        SpeedometerGauge(
            value = 175.0,
            min = 0.0,
            max = 200.0,
            unit = "Watts",
            label = "Live · LHM PSU",
            sizeDp = 220,
        )
    }
}

@Composable
@Preview
fun AnimatedNumberPreview() {
    PreviewSurface {
        AnimatedNumber(value = 0.004205, digits = 6)
    }
}

@Composable
@Preview
fun SparklinePreview() {
    val now = 1_700_000_000_000L
    val samples = List(40) { i ->
        SessionSample(
            timestampMs = now + i * 5_000L,
            kwh = i * 0.0008,
            pricePerKwh = 0.26 + (i % 6) * 0.005,
            currency = "EUR",
            costInCurrency = i * 0.0008 * 0.26,
        )
    }
    PreviewSurface {
        Sparkline(
            samples = samples,
            select = { it.kwh },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
    }
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PreviewScheme) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

private val PreviewScheme = darkColorScheme(
    primary = Color(0xFF7BD389),
    onPrimary = Color(0xFF003913),
    tertiary = Color(0xFFFFB454),
    error = Color(0xFFFF6B6B),
    background = Color(0xFF101418),
    surface = Color(0xFF1A1F25),
    surfaceVariant = Color(0xFF252B33),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFFB4BAC4),
    outline = Color(0xFF445164),
    outlineVariant = Color(0xFF2A323D),
)
