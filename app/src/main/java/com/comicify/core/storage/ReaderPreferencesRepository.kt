package com.comicify.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comicify.core.ui.theme.ThemeAccent
import com.comicify.core.ui.theme.ThemeChoice
import com.comicify.core.ui.theme.ThemeGround
import com.comicify.domain.model.ReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.readerPreferencesDataStore by preferencesDataStore(name = "reader_preferences")

data class OpenDefaults(
    val direction: ReadingDirection = ReadingDirection.LeftToRight,
    val bubblesOnOpen: Boolean = false,
    val guidedOnOpen: Boolean = false,
)

class ReaderPreferencesRepository(private val context: Context) {

    val volumeKeyPageTurnEnabled: Flow<Boolean> = flag(VOLUME_KEY_PAGE_TURN_ENABLED, default = true)
    suspend fun setVolumeKeyPageTurnEnabled(enabled: Boolean) = set(VOLUME_KEY_PAGE_TURN_ENABLED, enabled)

    val nightTintEnabled: Flow<Boolean> = flag(NIGHT_TINT_ENABLED, default = false)
    suspend fun setNightTintEnabled(enabled: Boolean) = set(NIGHT_TINT_ENABLED, enabled)

    val keepScreenOn: Flow<Boolean> = flag(KEEP_SCREEN_ON, default = true)
    suspend fun setKeepScreenOn(enabled: Boolean) = set(KEEP_SCREEN_ON, enabled)

    val onboardingSeen: Flow<Boolean> = flag(ONBOARDING_SEEN, default = false)
    suspend fun setOnboardingSeen(seen: Boolean) = set(ONBOARDING_SEEN, seen)

    val bubblesOnOpen: Flow<Boolean> = flag(BUBBLES_ON_OPEN, default = false)
    suspend fun setBubblesOnOpen(enabled: Boolean) = set(BUBBLES_ON_OPEN, enabled)

    val guidedOnOpen: Flow<Boolean> = flag(GUIDED_ON_OPEN, default = false)
    suspend fun setGuidedOnOpen(enabled: Boolean) = set(GUIDED_ON_OPEN, enabled)

    val bubbleScale: Flow<Float?> = context.readerPreferencesDataStore.data.map { it[BUBBLE_SCALE] }
    suspend fun setBubbleScale(scale: Float) = set(BUBBLE_SCALE, scale)

    val readingDirection: Flow<ReadingDirection> = flag(READING_DIRECTION_RTL, default = false).map { rtl ->
        if (rtl) ReadingDirection.RightToLeft else ReadingDirection.LeftToRight
    }

    suspend fun setReadingDirection(direction: ReadingDirection) =
        set(READING_DIRECTION_RTL, direction == ReadingDirection.RightToLeft)

    val openDefaults: Flow<OpenDefaults> =
        combine(readingDirection, bubblesOnOpen, guidedOnOpen) { direction, bubbles, guided ->
            OpenDefaults(direction = direction, bubblesOnOpen = bubbles, guidedOnOpen = guided)
        }

    val theme: Flow<ThemeChoice> = context.readerPreferencesDataStore.data.map {
        ThemeChoice(
            ground = it[THEME_GROUND]?.let(ThemeGround::valueOf) ?: ThemeChoice.Default.ground,
            accent = it[THEME_ACCENT]?.let(ThemeAccent::valueOf) ?: ThemeChoice.Default.accent,
        )
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.readerPreferencesDataStore.edit {
            it[THEME_GROUND] = choice.ground.name
            it[THEME_ACCENT] = choice.accent.name
        }
    }

    private fun flag(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        context.readerPreferencesDataStore.data.map { it[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.readerPreferencesDataStore.edit { it[key] = value }
    }

    private companion object {
        val VOLUME_KEY_PAGE_TURN_ENABLED = booleanPreferencesKey("volume_key_page_turn_enabled")
        val NIGHT_TINT_ENABLED = booleanPreferencesKey("night_tint_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val BUBBLES_ON_OPEN = booleanPreferencesKey("bubbles_on_open")
        val GUIDED_ON_OPEN = booleanPreferencesKey("guided_on_open")
        val READING_DIRECTION_RTL = booleanPreferencesKey("reading_direction_rtl")
        val BUBBLE_SCALE = floatPreferencesKey("bubble_scale")
        val THEME_GROUND = stringPreferencesKey("theme_ground")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
    }
}
