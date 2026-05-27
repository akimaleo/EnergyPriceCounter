package com.kawa.energy.counter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
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
 * Equirectangular world picker with country bounding-box wireframes.
 * Supports two-finger pinch/drag on touch, scroll-wheel zoom on desktop, and
 * single-tap to override the currently selected country.
 */
@Composable
fun WorldMap(
    selectedCountry: String?,
    onCountryPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val wireDefault = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val wireSupported = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val dotDefault = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
    val dotSupported = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

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
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (dy != 0f) {
                                    val zoomFactor = if (dy < 0) 1.15f else 1f / 1.15f
                                    val newScale = (scale * zoomFactor).coerceIn(1f, 12f)
                                    val effective = newScale / scale
                                    val centroid = event.changes.first().position
                                    pan = (pan - centroid) * effective + centroid
                                    scale = newScale
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panDelta, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 12f)
                        val effective = newScale / scale
                        pan = (pan - centroid) * effective + centroid + panDelta
                        scale = newScale
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        // Inverse-transform the tap back into pre-pan/scale coordinates.
                        val mapX = (tap.x - pan.x) / scale
                        val mapY = (tap.y - pan.y) / scale
                        // Stay within the world; out-of-bounds taps clamp to nearest edge.
                        val clampedX = mapX.coerceIn(0f, w)
                        val clampedY = mapY.coerceIn(0f, h)
                        val (lat, lon) = unproject(clampedX, clampedY, w, h)
                        CountryCentroids.nearest(lat, lon)?.let(onCountryPicked)
                    }
                },
        ) {
            val w = size.width
            val h = size.height

            // Apply pan & scale around the canvas content.
            withTransform({
                translate(pan.x, pan.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Lat/lon graticule
                (-180..180 step 30).forEach { lonDeg ->
                    val x = ((lonDeg + 180) / 360f) * w
                    drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f / scale)
                }
                (-90..90 step 30).forEach { latDeg ->
                    val y = ((90f - latDeg) / 180f) * h
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f / scale)
                }

                // Country bounding-box wireframes
                val stroke = (1.4f / scale).coerceAtLeast(0.6f)
                CountryCentroids.bboxes.forEach { (code, bbox) ->
                    val isSupported = code in SUPPORTED
                    val (x1, y1) = project(bbox.maxLat, bbox.minLon, w, h)
                    val (x2, y2) = project(bbox.minLat, bbox.maxLon, w, h)
                    val color = if (isSupported) wireSupported else wireDefault
                    drawRect(
                        color = color,
                        topLeft = Offset(x1, y1),
                        size = Size(x2 - x1, y2 - y1),
                        style = Stroke(width = stroke),
                    )
                }

                // Continent labels (only when zoomed out enough to keep them readable)
                if (scale < 3.5f) {
                    val style = TextStyle(
                        color = labelColor,
                        fontSize = (9f / scale.coerceAtLeast(1f)).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CONTINENT_LABELS.forEach { (text, point) ->
                        val (px, py) = project(point.lat, point.lon, w, h)
                        val layout = measurer.measure(text, style = style)
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                px - layout.size.width / 2f,
                                py - layout.size.height / 2f,
                            ),
                        )
                    }
                }

                // Country dots (slightly larger for supported countries)
                val baseR = with(density) { 2.5.dp.toPx() } / scale
                CountryCentroids.all.forEach { (code, p) ->
                    val (cx, cy) = project(p.lat, p.lon, w, h)
                    val supported = code in SUPPORTED
                    drawCircle(
                        color = if (supported) dotSupported else dotDefault,
                        radius = if (supported) baseR * 1.4f else baseR,
                        center = Offset(cx, cy),
                    )
                }

                // Highlight selected country (halo + pin + filled bbox)
                selectedCountry?.let { code ->
                    CountryCentroids.bboxFor(code)?.let { bbox ->
                        val (bx1, by1) = project(bbox.maxLat, bbox.minLon, w, h)
                        val (bx2, by2) = project(bbox.minLat, bbox.maxLon, w, h)
                        drawRect(
                            color = highlight.copy(alpha = 0.22f),
                            topLeft = Offset(bx1, by1),
                            size = Size(bx2 - bx1, by2 - by1),
                        )
                        drawRect(
                            color = highlight,
                            topLeft = Offset(bx1, by1),
                            size = Size(bx2 - bx1, by2 - by1),
                            style = Stroke(width = (2f / scale).coerceAtLeast(0.8f)),
                        )
                    }
                    CountryCentroids.forCountry(code)?.let { p ->
                        val (cx, cy) = project(p.lat, p.lon, w, h)
                        val haloR = with(density) { 8.dp.toPx() } / scale
                        drawCircle(
                            color = highlight.copy(alpha = 0.35f),
                            radius = haloR,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = highlight,
                            radius = with(density) { 3.5.dp.toPx() } / scale,
                            center = Offset(cx, cy),
                        )
                    }
                }
            }
        }

        // Zoom controls in the top-right corner — non-pointer-blocking.
        ZoomBadge(
            scaleText = formatScale(scale),
            onReset = { scale = 1f; pan = Offset.Zero },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}

@Composable
private fun ZoomBadge(
    scaleText: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { onReset() }
            },
    ) {
        Text(
            scaleText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
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

private fun formatScale(s: Float): String {
    val tenths = kotlin.math.round(s * 10f).toInt()
    if (tenths <= 10) return "1×"
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac×"
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
