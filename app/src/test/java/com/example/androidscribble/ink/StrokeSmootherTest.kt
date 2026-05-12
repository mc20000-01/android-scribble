package com.example.androidscribble.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StrokeSmootherTest {
    @Test
    fun finishWithoutBeginReturnsNull() {
        val stroke = ActiveStroke()

        assertNull(StrokeSmoother.finish(stroke))
    }

    @Test
    fun appendFiltersVeryShortNoisyMovement() {
        val stroke = ActiveStroke()
        StrokeSmoother.begin(stroke, InkPoint(0f, 0f, 0L))
        StrokeSmoother.append(stroke, InkPoint(0.5f, 0.5f, 1L))
        StrokeSmoother.append(stroke, InkPoint(1.0f, 1.0f, 2L))
        StrokeSmoother.append(stroke, InkPoint(2.0f, 0f, 3L))

        val finished = StrokeSmoother.finish(stroke)

        assertNotNull(finished)
        assertEquals(listOf(InkPoint(0f, 0f, 0L), InkPoint(2.0f, 0f, 3L)), finished?.points)
    }

    @Test
    fun beginResetsExistingPointsForDeterministicReuse() {
        val stroke = ActiveStroke()
        StrokeSmoother.begin(stroke, InkPoint(0f, 0f, 0L))
        StrokeSmoother.append(stroke, InkPoint(10f, 0f, 1L))

        StrokeSmoother.begin(stroke, InkPoint(5f, 5f, 2L))

        assertEquals(listOf(InkPoint(5f, 5f, 2L)), stroke.points)
    }
}
