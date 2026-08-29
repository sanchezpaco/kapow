package com.comicify.feature.review.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comicify.feature.review.domain.ReviewPromptState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reviewPromptDataStore by preferencesDataStore(name = "review_prompt")
private val finishedComicsKey = intPreferencesKey("finished_comics")
private val lastPromptedAtKey = longPreferencesKey("last_prompted_at")

@Singleton
class ReviewPromptPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun state(): ReviewPromptState = context.reviewPromptDataStore.data.first().let {
        ReviewPromptState(finishedComics = it[finishedComicsKey] ?: 0, lastPromptedAt = it[lastPromptedAtKey])
    }

    suspend fun recordComicFinished() {
        context.reviewPromptDataStore.edit { it[finishedComicsKey] = (it[finishedComicsKey] ?: 0) + 1 }
    }

    suspend fun recordPrompted(at: Long) {
        context.reviewPromptDataStore.edit { it[lastPromptedAtKey] = at }
    }
}
