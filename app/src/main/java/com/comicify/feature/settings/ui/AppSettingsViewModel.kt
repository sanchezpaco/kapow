package com.comicify.feature.settings.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.core.ui.theme.ThemeChoice
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppSettingsUiState(
    val direction: ReadingDirection = ReadingDirection.LeftToRight,
    val bubblesOnOpen: Boolean = false,
    val guidedOnOpen: Boolean = false,
    val bubbleScale: Float = BUBBLE_ENLARGE_SCALE,
    val volumeKeyPageTurn: Boolean = true,
    val nightTint: Boolean = false,
    val keepScreenOn: Boolean = true,
    val folderUri: String? = null,
    val theme: ThemeChoice = ThemeChoice.Default,
)

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    application: Application,
    library: LibraryRepository,
) : ViewModel() {

    private val preferences = ReaderPreferencesRepository(application)

    val state: StateFlow<AppSettingsUiState> = combine(
        preferences.openDefaults,
        preferences.bubbleScale,
        combine(preferences.volumeKeyPageTurnEnabled, preferences.nightTintEnabled, preferences.keepScreenOn, ::Triple),
        library.folderUri,
        preferences.theme,
    ) { defaults, bubbleScale, (volumeKeys, nightTint, keepScreenOn), folderUri, theme ->
        AppSettingsUiState(
            direction = defaults.direction,
            bubblesOnOpen = defaults.bubblesOnOpen,
            guidedOnOpen = defaults.guidedOnOpen,
            bubbleScale = bubbleScale ?: BUBBLE_ENLARGE_SCALE,
            volumeKeyPageTurn = volumeKeys,
            nightTint = nightTint,
            keepScreenOn = keepScreenOn,
            folderUri = folderUri,
            theme = theme,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettingsUiState())

    fun onDirectionSelected(direction: ReadingDirection) = save { preferences.setReadingDirection(direction) }
    fun onBubblesOnOpenChanged(enabled: Boolean) = save { preferences.setBubblesOnOpen(enabled) }
    fun onGuidedOnOpenChanged(enabled: Boolean) = save { preferences.setGuidedOnOpen(enabled) }
    fun onBubbleScaleChanged(scale: Float) = save { preferences.setBubbleScale(scale) }
    fun onVolumeKeyPageTurnChanged(enabled: Boolean) = save { preferences.setVolumeKeyPageTurnEnabled(enabled) }
    fun onNightTintChanged(enabled: Boolean) = save { preferences.setNightTintEnabled(enabled) }
    fun onKeepScreenOnChanged(enabled: Boolean) = save { preferences.setKeepScreenOn(enabled) }
    fun onThemeSelected(theme: ThemeChoice) = save { preferences.setTheme(theme) }
    fun onReplayOnboarding() = save { preferences.setOnboardingSeen(false) }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch { write() }
    }
}
