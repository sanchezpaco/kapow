package com.comicify.feature.review.domain

import java.util.concurrent.TimeUnit

data class ReviewPromptState(val finishedComics: Int, val lastPromptedAt: Long?)

object ReviewPromptPolicy {
    const val FINISHED_COMICS_BEFORE_PROMPT = 3
    val MIN_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(60)

    fun isDue(state: ReviewPromptState, now: Long): Boolean {
        if (state.finishedComics < FINISHED_COMICS_BEFORE_PROMPT) return false
        val last = state.lastPromptedAt ?: return true
        return now - last >= MIN_INTERVAL_MS
    }
}
