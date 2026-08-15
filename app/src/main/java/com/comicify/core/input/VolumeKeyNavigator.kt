package com.comicify.core.input

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

enum class PageTurnDirection { Next, Previous }

fun volumeKeyPageTurnDirection(keyCode: Int): PageTurnDirection? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> PageTurnDirection.Next
    KeyEvent.KEYCODE_VOLUME_UP -> PageTurnDirection.Previous
    else -> null
}

object VolumeKeyPageTurnDispatcher {
    private var listener: ((PageTurnDirection) -> Unit)? = null

    val isRegistered: Boolean
        get() = listener != null

    fun dispatch(direction: PageTurnDirection): Boolean {
        val current = listener ?: return false
        current(direction)
        return true
    }

    internal fun register(newListener: (PageTurnDirection) -> Unit) {
        listener = newListener
    }

    internal fun unregister(oldListener: (PageTurnDirection) -> Unit) {
        if (listener === oldListener) listener = null
    }
}

@Composable
fun RegisterVolumeKeyPageTurns(enabled: Boolean, onPageTurn: (PageTurnDirection) -> Unit) {
    val currentHandler by rememberUpdatedState(onPageTurn)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val listener: (PageTurnDirection) -> Unit = { currentHandler(it) }
        VolumeKeyPageTurnDispatcher.register(listener)
        onDispose { VolumeKeyPageTurnDispatcher.unregister(listener) }
    }
}
