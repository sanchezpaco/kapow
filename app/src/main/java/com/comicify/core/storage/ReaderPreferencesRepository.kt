package com.comicify.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerPreferencesDataStore by preferencesDataStore(name = "reader_preferences")

class ReaderPreferencesRepository(private val context: Context) {

    val volumeKeyPageTurnEnabled: Flow<Boolean> =
        context.readerPreferencesDataStore.data.map { it[VOLUME_KEY_PAGE_TURN_ENABLED] ?: true }

    suspend fun setVolumeKeyPageTurnEnabled(enabled: Boolean) {
        context.readerPreferencesDataStore.edit { it[VOLUME_KEY_PAGE_TURN_ENABLED] = enabled }
    }

    private companion object {
        val VOLUME_KEY_PAGE_TURN_ENABLED = booleanPreferencesKey("volume_key_page_turn_enabled")
    }
}
