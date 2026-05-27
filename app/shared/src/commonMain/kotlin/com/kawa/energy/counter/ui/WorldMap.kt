package com.kawa.energy.counter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kawa.energy.counter.location.CountryCentroids

/**
 * Minimalist equirectangular world picker. Renders supported countries as dots,
 * a halo on the currently selected one, and a center-marked label. Tapping anywhere
 * snaps to the nearest country dot.
 *
 * @param selectedCountry ISO-3166-1 alpha-2 country code to highlight (or null)
 * @param onCountryPicked invoked with the ISO code when the user taps somewhere
 */
@Composable
fun WorldMap(
    selectedCountry: String?,
    onCountryPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val landDot = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val supportedDot = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val (lat, lon) = unproject(offset.x, offset.y, w, h)
                        CountryCentroids.nearest(lat, lon)?.let(onCountryPicked)
                    }
                },
        ) {
            val w = size.width
            val h = size.height

            // Latitude/longitude gridlines
            // Verticals every 60°, horizontals every 30°.
            (-180..180 step 60).forEach { lonDeg ->
                val x = ((lonDeg + 180) / 360f) * w
                drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            (-60..60 step 30).forEach { latDeg ->
                val y = ((90f - latDeg) / 180f) * h
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            // Continent labels
            val labelStyle = TextStyle(
                color = labelColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
            CONTINENT_LABELS.forEach { (text, point) ->
                val (px, py) = project(point.lat, point.lon, w, h)
                val layout = measurer.measure(text, style = labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(px - layout.size.width / 2f, py - layout.size.height / 2f),
                )
            }

            // Country dots
            val baseRadius = with(density) { 3.dp.toPx() }
            CountryCentroids.all.forEach { (code, p) ->
                val (cx, cy) = project(p.lat, p.lon, w, h)
                val supported = code in SUPPORTED
                val color = if (supported) supportedDot else landDot
                drawCircle(color = color, radius = baseRadius, center = Offset(cx, cy))
            }

            // Selected halo + pin
            selectedCountry?.let { code ->
                val p = CountryCentroids.forCountry(code) ?: return@let
                val (cx, cy) = project(p.lat, p.lon, w, h)
                val haloR = with(density) { 12.dp.toPx() }
                drawCircle(highlight.copy(alpha = 0.35f), radius = haloR, center = Offset(cx, cy))
                drawCircle(highlight, radius = with(density) { 5.dp.toPx() }, center = Offset(cx, cy))
            }
        }

    }
}

private fun project(lat: Double, lon: Double, w: Float, h: Float): Pair<Float, Float> {
    val x = (((lon + 180.0) / 360.0) * w).toFloat()
    val y = (((90.0 - lat) / 180.0) * h).toFloat()
    return x to y
}

private fun unproject(x: Float, y: Float, w: Float, h: Float): Pair<Double, Double> {
    val lon = (x / w) * 360.0 - 180.0
    val lat = 90.0 - (y / h) * 180.0
    return lat to lon
}

private data class LabelPoint(val lat: Double, val lon: Double)

private val CONTINENT_LABELS: List<Pair<String, LabelPoint>> = listOf(
    "N. AMERICA" to LabelPoint(48.0, -100.0),
    "S. AMERICA" to LabelPoint(-15.0, -60.0),
    "EUROPE" to LabelPoint(54.0, 15.0),
    "AFRICA" to LabelPoint(5.0, 20.0),
    "ASIA" to LabelPoint(40.0, 90.0),
    "OCEANIA" to LabelPoint(-25.0, 140.0),
)

private val SUPPORTED: Set<String> = setOf(
    "DE", "AT", "NL", "BE", "FR", "CH", "LU", "PL", "PT", "ES", "IT", "DK",
    "NO", "SE", "FI", "HU", "CZ", "SK", "IE", "GR", "HR", "SI", "RO", "BG",
    "EE", "LV", "LT", "RS",
)

