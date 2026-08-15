package com.comicify

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.comicify.core.input.VolumeKeyPageTurnDispatcher
import com.comicify.core.input.volumeKeyPageTurnDirection
import com.comicify.core.ui.theme.ComicifyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComicifyTheme(useDarkTheme = true) {
                ComicifyRoot()
            }
        }
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
