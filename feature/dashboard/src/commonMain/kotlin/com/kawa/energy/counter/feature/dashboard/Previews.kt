package com.kawa.energy.counter.feature.dashboard

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
import com.kawa.energy.counter.domain.ElectricityRate
import com.kawa.energy.counter.domain.LocationInfo

@Composable
@Preview
fun StatusBadgeRunningPreview() {
    PreviewSurface { StatusBadge(running = true) }
}

@Composable
@Preview
fun StatusBadgeIdlePreview() {
    PreviewSurface { StatusBadge(running = false) }
}

@Composable
@Preview
fun HeaderRunningPreview() {
    val state = EnergyUiState(
        running = true,
        watts = 78.4,
        kwh = 0.00342,
        rate = ElectricityRate(0.2643, "EUR", "energy-charts:NL", "https://api.energy-charts.info/price?bzn=NL"),
        cost = 0.00091,
        currency = "EUR",
        location = LocationInfo("NL", "Utrecht, Netherlands", "ipinfo.io"),
        locationStatus = LocationStatus.Detected(LocationInfo("NL", "Utrecht, Netherlands", "ipinfo.io")),
    )
    PreviewSurface { Header(state = state) }
}

@Composable
@Preview
fun HeaderDetectingPreview() {
    val state = EnergyUiState(locationStatus = LocationStatus.Detecting)
    PreviewSurface { Header(state = state) }
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = previewScheme()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) { content() }
    }
}

private fun previewScheme() = darkColorScheme(
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
