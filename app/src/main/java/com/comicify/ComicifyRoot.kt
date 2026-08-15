package com.comicify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import com.comicify.feature.library.ui.LibraryScreen
import com.comicify.feature.reader.ui.ReaderScreen

@Composable
fun ComicifyRoot() {
    var openedUri by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background,
    ) {
        val current = openedUri
        if (current == null) {
            LibraryScreen(onOpenComic = { openedUri = it.toString() })
        } else {
            ReaderScreen(uri = current.toUri(), onClose = { openedUri = null })
        }
    }
}
