package com.comicify.core.storage

import android.content.Context
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

@Singleton
class LibraryPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val folderUri: Flow<String?> = context.libraryDataStore.data.map { it[folderUriKey] }

    suspend fun setFolderUri(uri: String) {
        context.libraryDataStore.edit { it[folderUriKey] = uri }
    }
}
