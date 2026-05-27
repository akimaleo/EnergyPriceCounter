package com.kawa.energy.counter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.MouseInfo
import java.awt.Point

fun main() = application {
    val state = rememberWindowState(width = 900.dp, height = 760.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "EnergyCounter",
        undecorated = true,
        state = state,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101418),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CustomTitleBar(state = state, onClose = ::exitApplication)
                Box(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun WindowScope.CustomTitleBar(state: WindowState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF0B0E11)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DraggableTitleArea(modifier = Modifier.weight(1f).fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(start = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFFD66B)),
                )
                Text(
                    text = "  EnergyCounter",
                    color = Color(0xFFE5E7EB),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }

        TitleBarButton(symbol = TitleBarSymbol.Minimize) { state.isMinimized = true }
        TitleBarButton(symbol = TitleBarSymbol.Maximize) {
            state.placement = if (state.placement == WindowPlacement.Maximized)
                WindowPlacement.Floating else WindowPlacement.Maximized
        }
        TitleBarButton(symbol = TitleBarSymbol.Close, isDanger = true, onClick = onClose)
    }
}

@Composable
private fun TitleBarButton(
    symbol: TitleBarSymbol,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    val fg = Color(0xFFB4BAC4)
    val hoverBg = if (isDanger) Color(0xFFCC2233) else Color(0xFF252B33)
    var hovered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 36.dp)
            .background(if (hovered) hoverBg else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Default)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val c = if (isDanger && hovered) Color.White else fg
            val sw = 1.4f
            when (symbol) {
                TitleBarSymbol.Minimize -> drawLine(
                    color = c,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = sw,
                )
                TitleBarSymbol.Maximize -> drawRect(
                    color = c,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    style = Stroke(width = sw),
                )
                TitleBarSymbol.Close -> {
                    drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), sw, cap = StrokeCap.Round)
                }
            }
        }
    }
}

private enum class TitleBarSymbol { Minimize, Maximize, Close }

/**
 * Draggable region for an undecorated window. Computes the cursor-to-window
 * offset in screen coordinates on press and pins the window to it on every
 * move. Using compose pointer-local deltas here causes a feedback loop:
 * moving the window shifts the pointer's local position, which compose then
 * reports as a delta on the next event, making the window oscillate.
 */
@Composable
private fun WindowScope.DraggableTitleArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val frame = window
    var grab: Point? = null
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    val mouse = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                    val win = frame.location
                    grab = Point(mouse.x - win.x, mouse.y - win.y)
                },
                onDragEnd = { grab = null },
                onDragCancel = { grab = null },
            ) { change, _ ->
                change.consume()
                val mouse = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                grab?.let { off -> frame.setLocation(mouse.x - off.x, mouse.y - off.y) }
            }
        },
        content = { content() },
    )
}
