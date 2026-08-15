package com.comicify.feature.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_preferences",
)

internal val NightTintEnabledKey = booleanPreferencesKey("night_tint_enabled")

internal fun Preferences.toNightTintEnabled(): Boolean = this[NightTintEnabledKey] ?: false

class ReaderPreferencesRepository(context: Context) {

    private val dataStore = context.readerPreferencesDataStore

    val nightTintEnabled: Flow<Boolean> = dataStore.data.map { it.toNightTintEnabled() }

    suspend fun setNightTintEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[NightTintEnabledKey] = enabled }
    }
}
