package com.comicify

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.comicify.core.input.VolumeKeyPageTurnDispatcher
import com.comicify.core.input.volumeKeyPageTurnDirection
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.core.ui.theme.ComicifyTheme
import com.comicify.core.ui.theme.ThemeChoice
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = resolveViewIntentUri()
        val preferences = ReaderPreferencesRepository(applicationContext)
        setContent {
            val theme by preferences.theme.collectAsStateWithLifecycle(ThemeChoice.Default)
            ComicifyTheme(choice = theme) {
                ComicifyRoot(initialUri = initialUri)
            }
        }
    }

    private fun resolveViewIntentUri(): Uri? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        takePersistableReadPermissionIfGranted(uri)
        return uri
    }

    private fun takePersistableReadPermissionIfGranted(uri: Uri) {
        val persistableGrant = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || !persistableGrant) return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val direction = volumeKeyPageTurnDirection(keyCode)
        if (direction != null && VolumeKeyPageTurnDispatcher.dispatch(direction)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val direction = volumeKeyPageTurnDirection(keyCode)
        if (direction != null && VolumeKeyPageTurnDispatcher.isRegistered) return true
        return super.onKeyUp(keyCode, event)
    }
}
