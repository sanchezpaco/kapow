package com.comicify.feature.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comicify.core.storage.ComicSettingsDao
import com.comicify.core.storage.ComicSettingsEntity
import com.comicify.core.storage.DatabaseEntryPoint
import com.comicify.core.storage.OpenDefaults
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.core.window.ReadingPosture
import com.comicify.domain.model.ReadingDirection
import com.comicify.domain.model.ReadingPosition
import com.comicify.feature.reader.data.ComicSource
import com.comicify.feature.reader.data.ComicSourceException
import com.comicify.feature.reader.data.ComicSourceFactory
import com.comicify.feature.reader.data.PageDetectionStore
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.data.PanelDetector
import com.comicify.feature.reader.data.SplitPagesComicSource
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import com.comicify.feature.reader.domain.ComicOpenError
import com.comicify.feature.reader.domain.ReaderViewMode
import com.comicify.feature.reader.domain.SplitSuggestion
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val READER_TAG = "Reader"

class ReaderViewModel(
    application: Application,
    private val uri: Uri,
    initialPage: Int,
    private val preferencesRepository: ReaderPreferencesRepository = ReaderPreferencesRepository(application),
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ReaderUiState(position = ReadingPosition(pageIndex = initialPage)))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()
    private val _shareRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<Intent> = _shareRequests.asSharedFlow()

    private val comicSettingsDao: ComicSettingsDao =
        EntryPointAccessors.fromApplication(application, DatabaseEntryPoint::class.java).comicSettingsDao()
    private var source: ComicSource? = null
    private val chainSources = mutableListOf<ComicSource>()
    private var sourceMode: SourceMode? = null
    var pageLoader: PageLoader? = null
        private set

    init {
        openComic()
        observeVolumeKeyPaging()
        observeNightTint()
        observeKeepScreenOn()
        observeBubbleScale()
        observeComicSettings()
    }

    private fun openComic() {
        viewModelScope.launch {
            val settings = comicSettingsDao.find(uri.toString())
            applyOpenDefaults(preferencesRepository.openDefaults.first(), settings)
            val mode = SourceMode(
                splitWidePages = settings?.splitWidePages ?: false,
                direction = effectiveDirection(preferencesRepository.readingDirection.first(), settings),
            )
            loadSource(mode) { state.value.position.pageIndex }
            if (!mode.splitWidePages && settings?.splitSuggested != true) suggestSplitIfMostlyWide()
        }
    }

    private suspend fun suggestSplitIfMostlyWide() {
        val opened = source ?: return
        val aspects = try {
            (0 until opened.pageCount).map { opened.pageAspect(it) }
        } catch (e: IOException) {
            Log.w(READER_TAG, "Page aspect probe failed, no split suggestion", e)
            return
        }
        if (SplitSuggestion.shouldSuggest(aspects)) _state.update { it.copy(splitSuggested = true) }
    }

    fun acceptSplitSuggestion() {
        _state.update { it.copy(splitSuggested = false) }
        updateSettings { it.copy(splitWidePages = true, splitSuggested = true) }
    }

    fun dismissSplitSuggestion() {
        _state.update { it.copy(splitSuggested = false) }
        updateSettings { it.copy(splitSuggested = true) }
    }

    private fun reopenComic(mode: SourceMode) {
        val previous = source ?: return
        val sourcePage = previous.sourcePage(state.value.position.pageIndex)
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            loadSource(mode) { opened -> opened.pageOfSourcePage(sourcePage) }
        }
    }

    private suspend fun loadSource(mode: SourceMode, landingPage: (ComicSource) -> Int) {
        val previous = source
        runCatching { openSource(mode) }
            .onSuccess { opened ->
                source = opened
                sourceMode = mode
                previous?.close()
                pageLoader = PageLoader(opened, viewModelScope, PanelDetector.forContext(getApplication()), detectionStore(mode))
                val page = landingPage(opened).coerceIn(0, opened.pageCount - 1)
                _state.update {
                    it.copy(loading = false, pageCount = opened.pageCount, position = it.position.copy(pageIndex = page))
                }
            }
            .onFailure { throwable ->
                val error = (throwable as? ComicSourceException)?.error ?: throw throwable
                _state.update { it.copy(loading = false, error = error) }
            }
    }

    private suspend fun openSource(mode: SourceMode): ComicSource {
        val opened = ComicSourceFactory.open(getApplication(), uri, state.value.position.pageIndex)
        if (!mode.splitWidePages) return opened
        return runCatching { SplitPagesComicSource.of(opened, mode.direction) }
            .onFailure { opened.close() }
            .getOrThrow()
    }

    private fun applyOpenDefaults(defaults: OpenDefaults, settings: ComicSettingsEntity?) {
        _state.update {
            it.copy(
                bubblesEnlarged = settings?.bubblesEnlarged ?: defaults.bubblesOnOpen,
                guided = settings?.guided ?: defaults.guidedOnOpen,
            )
        }
    }

    private fun detectionStore(mode: SourceMode): PageDetectionStore {
        val dao = EntryPointAccessors.fromApplication(getApplication(), DatabaseEntryPoint::class.java).pageDetectionDao()
        return PageDetectionStore(dao, uri.toString(), mode.splitWidePages)
    }

    suspend fun openChainIssue(issueUri: Uri): PageLoader? {
        val settings = comicSettingsDao.find(issueUri.toString())
        val split = settings?.splitWidePages ?: false
        val opened = runCatching {
            val source = ComicSourceFactory.open(getApplication(), issueUri, 0)
            if (split) SplitPagesComicSource.of(source, state.value.direction) else source
        }.getOrElse {
            Log.w(READER_TAG, "Could not open the next issue for the strip", it)
            return null
        }
        chainSources += opened
        comicSettingsDao.upsert((settings ?: emptySettings(issueUri.toString())).copy(verticalScroll = true))
        val dao = EntryPointAccessors.fromApplication(getApplication(), DatabaseEntryPoint::class.java).pageDetectionDao()
        return PageLoader(
            opened,
            viewModelScope,
            PanelDetector.forContext(getApplication()),
            PageDetectionStore(dao, issueUri.toString(), split),
        )
    }

    fun onPageChanged(pageIndex: Int) {
        _state.update { it.copy(position = it.position.copy(pageIndex = pageIndex)) }
    }

    fun hideChrome() {
        _state.update { if (it.chromeVisible) it.copy(chromeVisible = false) else it }
    }

    fun toggleChrome() {
        _state.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun requestJump(pageIndex: Int) {
        _state.update { it.copy(pendingJump = pageIndex) }
    }

    fun onJumpApplied() {
        _state.update { it.copy(pendingJump = null) }
    }

    fun setViewMode(mode: ReaderViewMode) {
        _state.update { it.copy(guided = mode == ReaderViewMode.Guided) }
        val strip = mode == ReaderViewMode.Strip
        if (_state.value.verticalScroll != strip) updateSettings { it.copy(verticalScroll = strip) }
    }

    fun toggleBubblesEnlarged() {
        _state.update { it.copy(bubblesEnlarged = !it.bubblesEnlarged) }
    }

    fun setBubbleScale(scale: Float) {
        viewModelScope.launch {
            val override = comicSettingsDao.find(uri.toString())?.takeIf { it.bubbleScale != null }
            if (override == null) {
                preferencesRepository.setBubbleScale(scale)
            } else {
                comicSettingsDao.upsert(override.copy(bubbleScale = scale))
            }
        }
    }

    private fun observeBubbleScale() {
        viewModelScope.launch {
            combine(preferencesRepository.bubbleScale, comicSettingsDao.observe(uri.toString())) { global, settings ->
                settings?.bubbleScale ?: global ?: BUBBLE_ENLARGE_SCALE
            }.collect { scale ->
                _state.update { it.copy(bubbleScale = scale) }
            }
        }
    }

    fun toggleGuidedFullScreen() {
        _state.update { it.copy(guidedFullScreen = !it.guidedFullScreen) }
    }

    fun setVolumeKeyPaging(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVolumeKeyPageTurnEnabled(enabled) }
    }

    private fun observeVolumeKeyPaging() {
        viewModelScope.launch {
            preferencesRepository.volumeKeyPageTurnEnabled.collect { enabled ->
                _state.update { it.copy(volumeKeyPagingEnabled = enabled) }
            }
        }
    }

    fun toggleNightTint() {
        viewModelScope.launch {
            preferencesRepository.setNightTintEnabled(!_state.value.nightTintEnabled)
        }
    }

    private fun observeKeepScreenOn() {
        viewModelScope.launch {
            preferencesRepository.keepScreenOn.collect { enabled ->
                _state.update { it.copy(keepScreenOn = enabled) }
            }
        }
    }

    private fun observeNightTint() {
        viewModelScope.launch {
            preferencesRepository.nightTintEnabled.collect { enabled ->
                _state.update { it.copy(nightTintEnabled = enabled) }
            }
        }
    }

    fun toggleReadingDirection() {
        val next = when (_state.value.direction) {
            ReadingDirection.LeftToRight -> ReadingDirection.RightToLeft
            ReadingDirection.RightToLeft -> ReadingDirection.LeftToRight
        }
        viewModelScope.launch {
            val override = comicSettingsDao.find(uri.toString())?.takeIf { it.rightToLeft != null }
            if (override == null) {
                preferencesRepository.setReadingDirection(next)
            } else {
                comicSettingsDao.upsert(override.copy(rightToLeft = next == ReadingDirection.RightToLeft))
            }
        }
    }

    fun toggleSplitWidePages() {
        val split = !_state.value.splitWidePages
        updateSettings { it.copy(splitWidePages = split) }
    }

    private fun updateSettings(transform: (ComicSettingsEntity) -> ComicSettingsEntity) {
        viewModelScope.launch {
            val settings = comicSettingsDao.find(uri.toString()) ?: emptySettings()
            comicSettingsDao.upsert(transform(settings))
        }
    }

    private fun emptySettings(documentUri: String = uri.toString()) = ComicSettingsEntity(
        documentUri = documentUri,
        rightToLeft = null,
        coverAlone = false,
        bubblesEnlarged = null,
        guided = null,
    )

    private fun observeComicSettings() {
        viewModelScope.launch {
            combine(preferencesRepository.readingDirection, comicSettingsDao.observe(uri.toString())) { global, settings ->
                ComicPreferences(
                    direction = effectiveDirection(global, settings),
                    coverAlone = settings?.coverAlone ?: false,
                    splitWidePages = settings?.splitWidePages ?: false,
                    verticalScroll = settings?.verticalScroll ?: false,
                )
            }.collect { preferences ->
                _state.update {
                    it.copy(
                        direction = preferences.direction,
                        coverAlone = preferences.coverAlone,
                        splitWidePages = preferences.splitWidePages,
                        verticalScroll = preferences.verticalScroll,
                    )
                }
                applySourceMode(SourceMode(preferences.splitWidePages, preferences.direction))
            }
        }
    }

    private fun applySourceMode(mode: SourceMode) {
        val current = sourceMode ?: return
        if (!current.rebuiltBy(mode)) return
        sourceMode = mode
        reopenComic(mode)
    }

    fun reportGlitch(posture: ReadingPosture) {
        val loader = pageLoader ?: return
        val current = _state.value
        val request = GlitchReportRequest(
            comicUri = uri,
            pageIndex = current.position.pageIndex,
            pageCount = current.pageCount,
            guided = current.guided,
            bubbleScale = current.bubbleScale.takeIf { current.bubblesEnlarged },
            posture = posture,
        )
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) { GlitchReport(getApplication()).compose(loader, request) }
            _shareRequests.emit(intent)
        }
    }

    override fun onCleared() {
        source?.close()
        chainSources.forEach { it.close() }
    }

    companion object {
        fun factory(application: Application, uri: Uri, initialPage: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReaderViewModel(application, uri, initialPage) }
        }
    }
}

private data class SourceMode(val splitWidePages: Boolean, val direction: ReadingDirection) {
    fun rebuiltBy(next: SourceMode): Boolean =
        next.splitWidePages != splitWidePages || (splitWidePages && next.direction != direction)
}

private data class ComicPreferences(
    val direction: ReadingDirection,
    val coverAlone: Boolean,
    val splitWidePages: Boolean,
    val verticalScroll: Boolean,
)

private fun effectiveDirection(global: ReadingDirection, settings: ComicSettingsEntity?): ReadingDirection =
    when (settings?.rightToLeft) {
        null -> global
        true -> ReadingDirection.RightToLeft
        false -> ReadingDirection.LeftToRight
    }

private fun ComicSource?.sourcePage(page: Int): Int = (this as? SplitPagesComicSource)?.sourcePageOf(page) ?: page

private fun ComicSource.pageOfSourcePage(sourcePage: Int): Int =
    (this as? SplitPagesComicSource)?.pageOfSource(sourcePage) ?: sourcePage
