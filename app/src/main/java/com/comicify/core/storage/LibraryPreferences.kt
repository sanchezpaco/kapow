package com.comicify.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryDataStore by preferencesDataStore(name = "library")
private val folderUriKey = stringPreferencesKey("folder_uri")
private val groupedKey = booleanPreferencesKey("grouped_by_series")
private val sampleSeededKey = booleanPreferencesKey("sample_seeded")

@Singleton
class LibraryPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val folderUri: Flow<String?> = context.libraryDataStore.data.map { it[folderUriKey] }

    val grouped: Flow<Boolean> = context.libraryDataStore.data.map { it[groupedKey] ?: false }

    val sampleSeeded: Flow<Boolean> = context.libraryDataStore.data.map { it[sampleSeededKey] ?: false }

    suspend fun setSampleSeeded() {
        context.libraryDataStore.edit { it[sampleSeededKey] = true }
    }

    suspend fun setFolderUri(uri: String) {
        context.libraryDataStore.edit { it[folderUriKey] = uri }
    }

    suspend fun setGrouped(grouped: Boolean) {
        context.libraryDataStore.edit { it[groupedKey] = grouped }
    }
}
