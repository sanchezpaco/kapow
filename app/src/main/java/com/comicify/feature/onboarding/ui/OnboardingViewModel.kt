package com.comicify.feature.onboarding.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.core.storage.ReaderPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(application: Application) : ViewModel() {

    private val preferences = ReaderPreferencesRepository(application)

    val seen: StateFlow<Boolean?> = preferences.onboardingSeen
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finish() {
        viewModelScope.launch { preferences.setOnboardingSeen(true) }
    }
}
