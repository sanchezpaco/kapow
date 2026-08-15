package com.comicify.feature.reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.comicify.domain.model.ReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_preferences")

private val READING_DIRECTION_RTL = booleanPreferencesKey("reading_direction_rtl")

class ReaderPreferences(private val context: Context) {

    val readingDirection: Flow<ReadingDirection> = context.readerDataStore.data.map { preferences ->
        if (preferences[READING_DIRECTION_RTL] == true) ReadingDirection.RightToLeft else ReadingDirection.LeftToRight
    }

    suspend fun setReadingDirection(direction: ReadingDirection) {
        context.readerDataStore.edit { preferences ->
            preferences[READING_DIRECTION_RTL] = direction == ReadingDirection.RightToLeft
        }
    }
}
