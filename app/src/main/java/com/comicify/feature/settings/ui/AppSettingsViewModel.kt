package com.comicify.feature.settings.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.core.ui.theme.ThemeChoice
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import com.comicify.feature.reader.domain.ReadingType
import com.comicify.feature.settings.data.LauncherIconRepository
import com.comicify.feature.settings.domain.LauncherIcon
import com.comicify.feature.stats.data.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppSettingsUiState(
    val readingType: ReadingType = ReadingType.Comic,
    val bubblesOnOpen: Boolean = false,
    val guidedOnOpen: Boolean = false,
    val bubbleScale: Float = BUBBLE_ENLARGE_SCALE,
    val volumeKeyPageTurn: Boolean = true,
    val nightTint: Boolean = false,
    val keepScreenOn: Boolean = true,
    val folderUri: String? = null,
    val theme: ThemeChoice = ThemeChoice.Default,
    val launcherIcon: LauncherIcon = LauncherIcon.Default,
)

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    application: Application,
    library: LibraryRepository,
    private val launcherIcons: LauncherIconRepository,
    private val stats: ReadingStatsRepository,
) : ViewModel() {

    private val preferences = ReaderPreferencesRepository(application)
    private val launcherIcon = MutableStateFlow(launcherIcons.current())

    val state: StateFlow<AppSettingsUiState> = combine(
        preferences.openDefaults,
        preferences.bubbleScale,
        combine(preferences.volumeKeyPageTurnEnabled, preferences.nightTintEnabled, preferences.keepScreenOn, ::Triple),
        library.folderUri,
        combine(preferences.theme, launcherIcon, ::Pair),
    ) { defaults, bubbleScale, (volumeKeys, nightTint, keepScreenOn), folderUri, (theme, icon) ->
        AppSettingsUiState(
            readingType = defaults.readingType,
            bubblesOnOpen = defaults.bubblesOnOpen,
            guidedOnOpen = defaults.guidedOnOpen,
            bubbleScale = bubbleScale ?: BUBBLE_ENLARGE_SCALE,
            volumeKeyPageTurn = volumeKeys,
            nightTint = nightTint,
            keepScreenOn = keepScreenOn,
            folderUri = folderUri,
            theme = theme,
            launcherIcon = icon,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettingsUiState())

    fun onReadingTypeSelected(type: ReadingType) = save { preferences.setReadingType(type) }
    fun onBubblesOnOpenChanged(enabled: Boolean) = save { preferences.setBubblesOnOpen(enabled) }
    fun onGuidedOnOpenChanged(enabled: Boolean) = save { preferences.setGuidedOnOpen(enabled) }
    fun onBubbleScaleChanged(scale: Float) = save { preferences.setBubbleScale(scale) }
    fun onVolumeKeyPageTurnChanged(enabled: Boolean) = save { preferences.setVolumeKeyPageTurnEnabled(enabled) }
    fun onNightTintChanged(enabled: Boolean) = save { preferences.setNightTintEnabled(enabled) }
    fun onKeepScreenOnChanged(enabled: Boolean) = save { preferences.setKeepScreenOn(enabled) }
    fun onThemeSelected(theme: ThemeChoice) = save { preferences.setTheme(theme) }

    fun onLauncherIconSelected(icon: LauncherIcon) {
        launcherIcons.select(icon)
        launcherIcon.value = launcherIcons.current()
    }

    fun onReplayOnboarding() = save { preferences.setOnboardingSeen(false) }
    fun onDeleteReadingHistory() = save { stats.deleteAll() }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch { write() }
    }
}
