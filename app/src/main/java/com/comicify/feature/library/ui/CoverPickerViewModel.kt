package com.comicify.feature.library.ui

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.data.PageThumbnails
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val COVER_PICKER_TAG = "CoverPicker"

data class CoverPickerUiState(val pageCount: Int = 0, val unreadable: Boolean = false)

@HiltViewModel
class CoverPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoverPickerUiState())
    val state: StateFlow<CoverPickerUiState> = _state.asStateFlow()
    private var pages: PageThumbnails? = null
    private var openedUri: String? = null

    fun open(documentUri: String) {
        if (openedUri == documentUri) return
        close()
        openedUri = documentUri
        viewModelScope.launch {
            val opened = runCatching { PageThumbnails.open(context, documentUri.toUri()) }
                .onFailure { Log.e(COVER_PICKER_TAG, "Cannot list the pages of $documentUri", it) }
                .getOrNull()
            if (openedUri != documentUri) {
                opened?.close()
                return@launch
            }
            pages = opened
            _state.value = CoverPickerUiState(pageCount = opened?.pageCount ?: 0, unreadable = opened == null)
        }
    }

    fun close() {
        openedUri = null
        pages?.close()
        pages = null
        _state.value = CoverPickerUiState()
    }

    suspend fun thumbnail(index: Int): ImageBitmap? = pages?.thumbnail(index)

    fun choose(comicId: Long, page: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.setCoverPage(comicId, page)) }
    }

    override fun onCleared() = close()
}
