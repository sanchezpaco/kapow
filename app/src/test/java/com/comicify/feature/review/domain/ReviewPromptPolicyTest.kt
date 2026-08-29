package com.comicify.feature.review.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {
    private val now = 1_000_000_000_000L

    @Test
    fun `not due before three finished comics`() {
        assertFalse(ReviewPromptPolicy.isDue(ReviewPromptState(finishedComics = 2, lastPromptedAt = null), now))
    }

    @Test
    fun `due on the third finished comic when never prompted`() {
        assertTrue(ReviewPromptPolicy.isDue(ReviewPromptState(finishedComics = 3, lastPromptedAt = null), now))
    }

    @Test
    fun `not due again within sixty days`() {
        val recently = now - ReviewPromptPolicy.MIN_INTERVAL_MS + 1
        assertFalse(ReviewPromptPolicy.isDue(ReviewPromptState(finishedComics = 10, lastPromptedAt = recently), now))
    }

    @Test
    fun `due again after sixty days`() {
        val longAgo = now - ReviewPromptPolicy.MIN_INTERVAL_MS
        assertTrue(ReviewPromptPolicy.isDue(ReviewPromptState(finishedComics = 10, lastPromptedAt = longAgo), now))
    }
}
