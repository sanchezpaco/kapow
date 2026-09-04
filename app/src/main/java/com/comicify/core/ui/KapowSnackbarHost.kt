package com.comicify.core.ui

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comicify.core.ui.theme.KapowTheme

@Composable
fun KapowSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val palette = KapowTheme.palette
    SnackbarHost(hostState = state, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = palette.raised,
            contentColor = palette.onGround,
            actionColor = palette.accent,
            dismissActionContentColor = palette.inkDim,
        )
    }
}
