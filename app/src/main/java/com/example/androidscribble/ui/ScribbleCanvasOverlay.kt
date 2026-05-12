package com.example.androidscribble.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt
import com.example.androidscribble.ink.ActiveStroke
import com.example.androidscribble.ink.InkContrastSampler
import com.example.androidscribble.ink.InkPoint
import com.example.androidscribble.ink.InkStroke
import com.example.androidscribble.ink.StrokeSmoother

@Composable
fun ScribbleCanvasOverlay(
    modifier: Modifier = Modifier,
    inkContrastSampler: InkContrastSampler,
    onStrokeFinished: (InkStroke) -> Unit,
) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val active = remember { mutableStateOf<ActiveStroke?>(null) }
    val hostView = LocalView.current

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                    val stroke = ActiveStroke()
                    val start = down.toInkPoint()
                    active.value = stroke
                    val hostLocation = IntArray(2)
                    hostView.getLocationOnScreen(hostLocation)
                    val screenPoint = Offset(
                        hostLocation[0] + down.position.x.roundToInt().toFloat(),
                        hostLocation[1] + down.position.y.roundToInt().toFloat(),
                    )
                    inkContrastSampler.sampleFromScreen(screenPoint) { color -> stroke.color = color }
                    StrokeSmoother.begin(stroke, start)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        StrokeSmoother.append(stroke, change.toInkPoint())
                        change.consume()
                    }
                    StrokeSmoother.finish(stroke)?.let { finished ->
                        strokes += finished
                        onStrokeFinished(finished)
                    }
                    active.value = null
                    hostView.invalidate()
                }
            }
    ) {
        strokes.takeLast(8).forEach { stroke ->
            drawPath(stroke.path, stroke.color, style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        active.value?.let { stroke ->
            drawPath(stroke.path, stroke.color, style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun PointerInputChange.toInkPoint(): InkPoint = InkPoint(position.x, position.y, uptimeMillis)
