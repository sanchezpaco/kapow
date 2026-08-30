package com.comicify.feature.reader.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitSuggestionTest {

    private val portrait = 0.66f
    private val wide = 1.33f

    @Test
    fun suggestsWhenEveryPageAfterTheCoverIsWide() {
        assertTrue(SplitSuggestion.shouldSuggest(listOf(portrait, wide, wide, wide, wide)))
    }

    @Test
    fun suggestsAtTheEightyPercentShare() {
        assertTrue(SplitSuggestion.shouldSuggest(listOf(portrait, wide, wide, wide, wide, portrait)))
    }

    @Test
    fun doesNotSuggestBelowTheShare() {
        assertFalse(SplitSuggestion.shouldSuggest(listOf(portrait, wide, wide, wide, portrait, portrait)))
    }

    @Test
    fun doesNotSuggestForAnOccasionalSplash() {
        assertFalse(SplitSuggestion.shouldSuggest(List(20) { portrait } + wide))
    }

    @Test
    fun doesNotSuggestForTooFewPages() {
        assertFalse(SplitSuggestion.shouldSuggest(listOf(portrait, wide)))
        assertFalse(SplitSuggestion.shouldSuggest(emptyList()))
    }

    @Test
    fun ignoresTheCoverOrientation() {
        assertTrue(SplitSuggestion.shouldSuggest(listOf(wide, wide, wide, wide)))
    }
}
