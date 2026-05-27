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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kawa.energy.counter.data.location.CountryCentroids
import energycounter.app.shared.generated.resources.Res
import energycounter.app.shared.generated.resources.world_wireframe
import org.jetbrains.compose.resources.painterResource

/**
 * Equirectangular world picker with a real raster map background.
 * Supports two-finger pinch / drag pan, single-tap to override, and
 * Ctrl/Cmd + scroll-wheel zoom (so plain scroll still scrolls the page).
 */
@Composable
fun WorldMap(
    selectedCountry: String?,
    onCountryPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    // Tinting the vector wireframe to onSurface keeps contrast correct in
    // both light and dark themes (dark lines on light bg, bright on dark).
    val wireframeTint = ColorFilter.tint(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    )
    val dotDefault = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    val dotSupported = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    val worldPainter = painterResource(Res.drawable.world_wireframe)

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
                            if (event.type != PointerEventType.Scroll) continue
                            val mods = event.keyboardModifiers
                            // Only intercept when Ctrl (Win/Linux) or Cmd/Meta (macOS) is held.
                            if (!mods.isCtrlPressed && !mods.isMetaPressed) continue
                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy == 0f) continue
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
                        val mapX = ((tap.x - pan.x) / scale).coerceIn(0f, w)
                        val mapY = ((tap.y - pan.y) / scale).coerceIn(0f, h)
                        val (lat, lon) = unproject(mapX, mapY, w, h)
                        CountryCentroids.nearest(lat, lon)?.let(onCountryPicked)
                    }
                },
        ) {
            val w = size.width
            val h = size.height

            withTransform({
                translate(pan.x, pan.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Vector wireframe map drawn to fill the equirectangular world rect.
                with(worldPainter) {
                    draw(size = Size(w, h), colorFilter = wireframeTint)
                }

                // Country dots (supported countries get a brighter, larger marker)
                val baseR = with(density) { 2.5.dp.toPx() } / scale
                CountryCentroids.all.forEach { (code, p) ->
                    val (cx, cy) = project(p.lat, p.lon, w, h)
                    val supported = code in SUPPORTED
                    drawCircle(
                        color = if (supported) dotSupported else dotDefault,
                        radius = if (supported) baseR * 1.5f else baseR,
                        center = Offset(cx, cy),
                    )
                }

                // Selected-country halo + pin + bbox outline.
                selectedCountry?.let { code ->
                    CountryCentroids.bboxFor(code)?.let { bbox ->
                        val (x1, y1) = project(bbox.maxLat, bbox.minLon, w, h)
                        val (x2, y2) = project(bbox.minLat, bbox.maxLon, w, h)
                        drawRect(
                            color = highlight.copy(alpha = 0.20f),
                            topLeft = Offset(x1, y1),
                            size = Size(x2 - x1, y2 - y1),
                        )
                        drawRect(
                            color = highlight,
                            topLeft = Offset(x1, y1),
                            size = Size(x2 - x1, y2 - y1),
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

        ZoomBadge(
            scaleText = formatScale(scale),
            onReset = { scale = 1f; pan = Offset.Zero },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )

        // Hint in the bottom-left so the user knows about Ctrl/Cmd.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        ) {
            Text(
                "Ctrl/⌘ + scroll to zoom · drag to pan",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
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

private val SUPPORTED: Set<String> = setOf(
    "DE", "AT", "NL", "BE", "FR", "CH", "LU", "PL", "PT", "ES", "IT", "DK",
    "NO", "SE", "FI", "HU", "CZ", "SK", "IE", "GR", "HR", "SI", "RO", "BG",
    "EE", "LV", "LT", "RS",
)
