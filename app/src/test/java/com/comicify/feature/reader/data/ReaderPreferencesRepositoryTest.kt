package com.comicify.feature.reader.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPreferencesRepositoryTest {

    @Test
    fun defaultsToDisabledWhenKeyMissing() {
        assertEquals(false, emptyPreferences().toNightTintEnabled())
    }

    @Test
    fun readsEnabledValue() {
        val preferences = mutablePreferencesOf(NightTintEnabledKey to true)
        assertEquals(true, preferences.toNightTintEnabled())
    }

    @Test
    fun readsDisabledValue() {
        val preferences = mutablePreferencesOf(NightTintEnabledKey to false)
        assertEquals(false, preferences.toNightTintEnabled())
    }
}
