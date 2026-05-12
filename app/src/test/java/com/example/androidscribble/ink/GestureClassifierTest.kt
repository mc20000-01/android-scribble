package com.example.androidscribble.ink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureClassifierTest {
    @Test
    fun scratchDeleteStrokeWithRepeatedHorizontalTurnsIsClassified() {
        val stroke = strokeOf(
            0f to 10f,
            20f to 12f,
            4f to 14f,
            24f to 16f,
            8f to 18f,
            28f to 20f,
        )

        assertEquals(ScribbleGesture.ScratchDelete, GestureClassifier.classify(stroke))
    }

    @Test
    fun verticalSlashStrokeIsClassified() {
        val stroke = strokeOf(
            20f to 0f,
            22f to 20f,
            21f to 40f,
            23f to 60f,
        )

        assertEquals(ScribbleGesture.VerticalSlash, GestureClassifier.classify(stroke))
    }

    @Test
    fun veryShortNoisyStrokeDefaultsToText() {
        val stroke = strokeOf(
            10f to 10f,
            10.4f to 10.2f,
            10.7f to 9.8f,
        )

        assertEquals(ScribbleGesture.Text, GestureClassifier.classify(stroke))
    }

    @Test
    fun singlePointStrokeDefaultsToText() {
        val stroke = strokeOf(10f to 10f)

        assertEquals(ScribbleGesture.Text, GestureClassifier.classify(stroke))
    }

    private fun strokeOf(vararg points: Pair<Float, Float>): InkStroke = InkStroke(
        points = points.mapIndexed { index, (x, y) -> InkPoint(x, y, index.toLong()) },
        path = Path(),
        color = Color.White,
    )
}
