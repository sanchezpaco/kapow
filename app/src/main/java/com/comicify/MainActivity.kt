package com.comicify

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.comicify.core.ui.splash.ColdStartSplash
import com.comicify.core.ui.splash.SplashOverlay
import com.comicify.core.input.VolumeKeyPageTurnDispatcher
import com.comicify.core.input.volumeKeyPageTurnDirection
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.core.ui.theme.KapowTheme
import com.comicify.core.ui.theme.ThemeChoice
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val initialUri = resolveViewIntentUri()
        val animateColdStart = ColdStartSplash.claim(this) && initialUri == null
        val preferences = ReaderPreferencesRepository(applicationContext)
        setContent {
            val theme by preferences.theme.collectAsStateWithLifecycle(ThemeChoice.Default)
            LaunchedEffect(theme.ground.light) { enableEdgeToEdge(systemBarStyle(theme.ground.light)) }
            var splashPlaying by remember { mutableStateOf(animateColdStart) }
            KapowTheme(choice = theme) {
                Box {
                    KapowRoot(initialUri = initialUri)
                    if (splashPlaying) SplashOverlay(onFinished = { splashPlaying = false })
                }
            }
        }
    }

    private fun systemBarStyle(lightGround: Boolean): SystemBarStyle =
        if (lightGround) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)

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
