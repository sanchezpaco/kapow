package com.comicify.feature.review.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.review.data.ReviewPromptPreferences
import com.comicify.feature.review.domain.ReviewPromptPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewPromptViewModel @Inject constructor(
    private val preferences: ReviewPromptPreferences,
) : ViewModel() {

    private val prompts = MutableSharedFlow<Unit>()
    val promptRequests: SharedFlow<Unit> = prompts

    fun onComicFinished() {
        viewModelScope.launch { preferences.recordComicFinished() }
    }

    fun onReaderClosed() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (!ReviewPromptPolicy.isDue(preferences.state(), now)) return@launch
            preferences.recordPrompted(now)
            prompts.emit(Unit)
        }
    }
}
