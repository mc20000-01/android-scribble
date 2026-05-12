package com.example.androidscribble.ml

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomDictionaryTest {
    private lateinit var dictionary: CustomDictionary

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dictionary = CustomDictionary(context)
        dictionary.entries().forEach(dictionary::remove)
    }

    @Test
    fun addIgnoresBlankTerms() {
        dictionary.add("   ")
        dictionary.add("\n\t")

        assertTrue(dictionary.entries().isEmpty())
    }

    @Test
    fun boostPromotesDictionaryEntriesWhilePreservingCandidateOrderWithinGroups() {
        dictionary.add(" beta ")
        dictionary.add("delta")

        val boosted = dictionary.boost(listOf("alpha", "beta", "gamma", "delta"))

        assertEquals(listOf("beta", "delta", "alpha", "gamma"), boosted)
    }

    @Test
    fun boostLeavesCandidatesStableWhenDictionaryIsEmpty() {
        val candidates = listOf("alpha", "beta", "gamma")

        assertEquals(candidates, dictionary.boost(candidates))
    }
}
