package com.comicify.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comicify.domain.model.ReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerPreferencesDataStore by preferencesDataStore(name = "reader_preferences")

class ReaderPreferencesRepository(private val context: Context) {

    val volumeKeyPageTurnEnabled: Flow<Boolean> =
        context.readerPreferencesDataStore.data.map { it[VOLUME_KEY_PAGE_TURN_ENABLED] ?: true }

    suspend fun setVolumeKeyPageTurnEnabled(enabled: Boolean) {
        context.readerPreferencesDataStore.edit { it[VOLUME_KEY_PAGE_TURN_ENABLED] = enabled }
    }

    val nightTintEnabled: Flow<Boolean> =
        context.readerPreferencesDataStore.data.map { it[NIGHT_TINT_ENABLED] ?: false }

    suspend fun setNightTintEnabled(enabled: Boolean) {
        context.readerPreferencesDataStore.edit { it[NIGHT_TINT_ENABLED] = enabled }
    }

    val bubbleScale: Flow<Float?> =
        context.readerPreferencesDataStore.data.map { it[BUBBLE_SCALE] }

    suspend fun setBubbleScale(scale: Float) {
        context.readerPreferencesDataStore.edit { it[BUBBLE_SCALE] = scale }
    }

    val readingDirection: Flow<ReadingDirection> =
        context.readerPreferencesDataStore.data.map {
            if (it[READING_DIRECTION_RTL] == true) ReadingDirection.RightToLeft else ReadingDirection.LeftToRight
        }

    suspend fun setReadingDirection(direction: ReadingDirection) {
        context.readerPreferencesDataStore.edit {
            it[READING_DIRECTION_RTL] = direction == ReadingDirection.RightToLeft
        }
    }

    private companion object {
        val VOLUME_KEY_PAGE_TURN_ENABLED = booleanPreferencesKey("volume_key_page_turn_enabled")
        val NIGHT_TINT_ENABLED = booleanPreferencesKey("night_tint_enabled")
        val READING_DIRECTION_RTL = booleanPreferencesKey("reading_direction_rtl")
        val BUBBLE_SCALE = floatPreferencesKey("bubble_scale")
    }
}
