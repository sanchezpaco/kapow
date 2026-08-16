package com.comicify

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.ui.LibraryScreen
import com.comicify.feature.library.ui.LibraryViewModel
import com.comicify.feature.reader.ui.ReaderScreen

private data class OpenRequest(val uri: Uri, val comicId: Long?, val initialPage: Int)

@Composable
fun ComicifyRoot(initialUri: Uri? = null) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember {
        mutableStateOf(initialUri?.let { OpenRequest(uri = it, comicId = null, initialPage = 0) })
    }

    Surface(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background,
    ) {
        val request = open
        if (request == null) {
            LibraryScreen(
                state = state,
                onFolderPicked = viewModel::onFolderPicked,
                onRefresh = viewModel::onRefresh,
                onOpenComic = { open = it.toOpenRequest() },
                onOpenFile = { open = OpenRequest(uri = it, comicId = null, initialPage = 0) },
                onFilterSelected = viewModel::onFilterSelected,
                onToggleRead = viewModel::onToggleRead,
                onToggleFavorite = viewModel::onToggleFavorite,
            )
        } else {
            ReaderScreen(
                uri = request.uri,
                initialPage = request.initialPage,
                onPageChanged = { pageIndex, pageCount ->
                    request.comicId?.let { viewModel.saveProgress(it, pageIndex, pageCount) }
                },
                onClose = { open = null },
            )
        }
    }
}

private fun LibraryComic.toOpenRequest(): OpenRequest =
    OpenRequest(uri = documentUri.toUri(), comicId = id, initialPage = pageIndex)
