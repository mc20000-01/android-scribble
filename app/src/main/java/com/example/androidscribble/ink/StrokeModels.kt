package com.example.androidscribble.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.hypot

/** A sampled pointer coordinate plus timestamp used by Digital Ink recognition. */
data class InkPoint(val x: Float, val y: Float, val t: Long)

data class InkStroke(
    val points: List<InkPoint>,
    val path: Path,
    val color: Color,
)

data class ActiveStroke(
    val points: MutableList<InkPoint> = mutableListOf(),
    val path: Path = Path(),
    var color: Color = Color.White,
)

object StrokeSmoother {
    fun begin(stroke: ActiveStroke, point: InkPoint) {
        stroke.points.clear()
        stroke.points += point
        stroke.path.reset()
        stroke.path.moveTo(point.x, point.y)
    }

    fun append(stroke: ActiveStroke, point: InkPoint) {
        val previous = stroke.points.lastOrNull() ?: return begin(stroke, point)
        if (hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) < 1.5) return
        stroke.points += point
        val mid = Offset((previous.x + point.x) / 2f, (previous.y + point.y) / 2f)
        stroke.path.quadraticBezierTo(previous.x, previous.y, mid.x, mid.y)
    }

    fun finish(stroke: ActiveStroke): InkStroke? {
        val last = stroke.points.lastOrNull() ?: return null
        stroke.path.lineTo(last.x, last.y)
        return InkStroke(stroke.points.toList(), Path().apply { addPath(stroke.path) }, stroke.color)
    }
}

enum class ScribbleGesture { ScratchDelete, CircleSelect, VerticalSlash, Text }

object GestureClassifier {
    fun classify(stroke: InkStroke): ScribbleGesture {
        val points = stroke.points
        if (points.size < 2) return ScribbleGesture.Text
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val distance = points.zipWithNext().sumOf { (a, b) -> hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }
        val horizontalTurns = points.zipWithNext().zipWithNext().count { (first, second) ->
            val dx1 = first.second.x - first.first.x
            val dx2 = second.second.x - second.first.x
            dx1 * dx2 < 0 && abs(dx1) > 4f && abs(dx2) > 4f
        }
        val start = points.first()
        val end = points.last()
        val closed = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()) < (width + height) * 0.18

        return when {
            horizontalTurns >= 3 && width > height * 1.4f -> ScribbleGesture.ScratchDelete
            closed && distance > (width + height) * 1.7f && width > 32f && height > 18f -> ScribbleGesture.CircleSelect
            height > width * 3f && height > 48f -> ScribbleGesture.VerticalSlash
            else -> ScribbleGesture.Text
        }
    }
}
