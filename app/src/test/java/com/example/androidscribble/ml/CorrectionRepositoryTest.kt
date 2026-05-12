package com.example.androidscribble.ml

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.test.core.app.ApplicationProvider
import com.example.androidscribble.ink.InkPoint
import com.example.androidscribble.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CorrectionRepositoryTest {
    private lateinit var repository: CorrectionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = CorrectionRepository(context)
        repository.clear()
    }

    @Test
    fun biasPromotesRepeatedCorrectionsAboveSingleCorrections() {
        repository.remember("helo", "hello", listOf(stroke()))
        repository.remember("helo", "hello", listOf(stroke()))
        repository.remember("hullo", "hallo", listOf(stroke()))

        val biased = repository.bias(listOf("hallo", "yellow", "hello"))

        assertEquals(listOf("hello", "hallo", "yellow"), biased)
    }

    @Test
    fun biasPreservesCandidateOrderWhenCorrectionCountsTie() {
        repository.remember("teh", "the", listOf(stroke()))
        repository.remember("recieve", "receive", listOf(stroke()))

        val biased = repository.bias(listOf("receive", "other", "the"))

        assertEquals(listOf("receive", "the", "other"), biased)
    }

    @Test
    fun biasLeavesCandidatesStableWhenNoCorrectionsExist() {
        val candidates = listOf("alpha", "beta", "gamma")

        assertEquals(candidates, repository.bias(candidates))
    }

    private fun stroke(): InkStroke = InkStroke(
        points = listOf(InkPoint(0f, 0f, 0L), InkPoint(4f, 1f, 1L)),
        path = Path(),
        color = Color.White,
    )
}
