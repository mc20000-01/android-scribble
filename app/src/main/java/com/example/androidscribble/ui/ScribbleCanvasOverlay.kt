package com.example.androidscribble.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.example.androidscribble.ink.ActiveStroke
import com.example.androidscribble.ink.InkContrastSampler
import com.example.androidscribble.ink.InkPoint
import com.example.androidscribble.ink.InkStroke
import com.example.androidscribble.ink.StrokeSmoother
import com.example.androidscribble.settings.ScribbleSettings

@Composable
fun ScribbleCanvasOverlay(
    modifier: Modifier = Modifier,
    settings: ScribbleSettings,
    inkContrastSampler: InkContrastSampler,
    onStrokeFinished: (InkStroke) -> Unit,
) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val active = remember { mutableStateOf<ActiveStroke?>(null) }
    val hostView = LocalView.current

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(settings) {
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                    val stroke = ActiveStroke()
                    active.value = stroke
                    if (settings.dynamicInkContrast) {
                        inkContrastSampler.sampleFromWindow(null, down.position) { color -> stroke.color = color }
                    } else {
                        stroke.color = inkContrastSampler.highContrastFallback(settings.darkFallbackInk)
                    }
                    StrokeSmoother.begin(stroke, down.toInkPoint())
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        StrokeSmoother.append(stroke, change.toInkPoint(), settings)
                        change.consume()
                    }
                    StrokeSmoother.finish(stroke)?.let { finished ->
                        strokes += finished
                        while (strokes.size > settings.keepRecentStrokes) strokes.removeAt(0)
                        onStrokeFinished(finished)
                    }
                    active.value = null
                    hostView.invalidate()
                }
            }
    ) {
        strokes.forEach { stroke -> drawPressureStroke(stroke, settings) }
        active.value?.let { stroke ->
            val preview = InkStroke(stroke.points, stroke.path, stroke.color)
            drawPressureStroke(preview, settings)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPressureStroke(
    stroke: InkStroke,
    settings: ScribbleSettings,
) {
    if (!settings.pressureEnabled || stroke.points.size < 2) {
        drawPath(stroke.path, stroke.color, style = Stroke(width = settings.baseStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    stroke.points.zipWithNext().forEach { (start, end) ->
        val width = settings.baseStrokeWidth * ((start.pressure + end.pressure) / 2f)
        drawLine(
            color = stroke.color,
            start = androidx.compose.ui.geometry.Offset(start.x, start.y),
            end = androidx.compose.ui.geometry.Offset(end.x, end.y),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private fun PointerInputChange.toInkPoint(): InkPoint = InkPoint(position.x, position.y, uptimeMillis)
