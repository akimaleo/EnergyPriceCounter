package com.kawa.energy.counter.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
fun EnergyHistoryChartPreview() {
    val now = 1_700_000_000_000L
    val samples = buildList {
        // First session: 60s, smooth ramp
        repeat(12) { i ->
            val ts = now + i * 5_000L
            val kwh = i * 0.0006
            add(
                SessionSample(
                    timestampMs = ts,
                    kwh = kwh,
                    pricePerKwh = 0.28,
                    currency = "EUR",
                    costInCurrency = kwh * 0.28,
                    countryCode = "NL",
                )
            )
        }
        // Gap (paused ~5 min)
        // Second session: a bit later, slightly higher draw
        val gap = now + 12 * 5_000L + 300_000L
        repeat(10) { i ->
            val ts = gap + i * 5_000L
            val kwh = i * 0.0008
            add(
                SessionSample(
                    timestampMs = ts,
                    kwh = kwh,
                    pricePerKwh = 0.31,
                    currency = "EUR",
                    costInCurrency = kwh * 0.31,
                    countryCode = "NL",
                )
            )
        }
    }
    MaterialTheme(colorScheme = previewScheme()) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            EnergyHistoryChart(samples = samples, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
@Preview
fun EnergyHistoryChartEmptyPreview() {
    MaterialTheme(colorScheme = previewScheme()) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            EnergyHistoryChart(samples = emptyList(), modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun previewScheme() = darkColorScheme(
    primary = Color(0xFF7BD389),
    error = Color(0xFFFF6B6B),
    background = Color(0xFF101418),
    surface = Color(0xFF1A1F25),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFFB4BAC4),
    outlineVariant = Color(0xFF2A323D),
)
