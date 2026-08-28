package com.comicify

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.ui.ComicSettingsScreen
import com.comicify.feature.library.ui.LibraryScreen
import com.comicify.feature.library.ui.LibraryViewModel
import com.comicify.feature.library.ui.LocalAnimatedVisibilityScope
import com.comicify.feature.library.ui.LocalSharedTransitionScope
import com.comicify.feature.onboarding.ui.OnboardingScreen
import com.comicify.feature.onboarding.ui.OnboardingViewModel
import com.comicify.feature.reader.ui.ReaderScreen
import com.comicify.feature.settings.ui.AppSettingsScreen
import com.comicify.feature.settings.ui.LicencesScreen

private const val SCREEN_FADE_MS = 350
private const val READER_ENTER_SCALE = 0.94f

private data class OpenRequest(val uri: Uri, val comicId: Long?, val initialPage: Int, val ambient: Color?)

private sealed interface Screen {
    data object Onboarding : Screen
    data object Library : Screen
    data class Settings(val comics: List<LibraryComic>) : Screen
    data object AppSettings : Screen
    data object Licences : Screen
    data class Reader(val request: OpenRequest) : Screen
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ComicifyRoot(initialUri: Uri? = null) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onboarding: OnboardingViewModel = hiltViewModel()
    val onboardingSeen by onboarding.seen.collectAsStateWithLifecycle()
    var open by remember {
        mutableStateOf(initialUri?.let { OpenRequest(uri = it, comicId = null, initialPage = 0, ambient = null) })
    }
    var settingsOf by remember { mutableStateOf<List<LibraryComic>?>(null) }
    var appSettingsOpen by remember { mutableStateOf(false) }
    var licencesOpen by remember { mutableStateOf(false) }
    val seen = onboardingSeen ?: return
    val screen: Screen = open?.let { Screen.Reader(it) }
        ?: if (!seen) Screen.Onboarding
        else settingsOf?.let { Screen.Settings(it) }
        ?: if (licencesOpen) Screen.Licences
        else if (appSettingsOpen) Screen.AppSettings else Screen.Library

    Surface(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background,
    ) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = screen,
                contentKey = { it.key() },
                transitionSpec = {
                    val enter = fadeIn(tween(SCREEN_FADE_MS)) +
                        if (targetState is Screen.Reader) scaleIn(tween(SCREEN_FADE_MS), initialScale = READER_ENTER_SCALE) else fadeIn()
                    enter togetherWith fadeOut(tween(SCREEN_FADE_MS))
                },
                label = "screen",
            ) { target ->
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalAnimatedVisibilityScope provides this@AnimatedContent,
                ) {
                    when (target) {
                        Screen.Onboarding -> OnboardingScreen(
                            onFolderPicked = viewModel::onFolderPicked,
                            onFinished = { appSettingsOpen = false; onboarding.finish() },
                        )
                        Screen.Library -> LibraryScreen(
                            state = state,
                            onFolderPicked = viewModel::onFolderPicked,
                            onOpenComic = { open = it.toOpenRequest() },
                            onOpenSettings = { settingsOf = it },
                            onOpenAppSettings = { appSettingsOpen = true },
                            onOpenFile = { open = OpenRequest(uri = it, comicId = null, initialPage = 0, ambient = null) },
                            onFilterSelected = viewModel::onFilterSelected,
                            onToggleGrouped = viewModel::onToggleGrouped,
                            onUnshelve = viewModel::onUnshelve,
                            onReshelve = viewModel::onReshelve,
                            onToggleRead = viewModel::onToggleRead,
                            onSetSeriesRead = viewModel::onSetSeriesRead,
                            onSetSeriesFavorite = viewModel::onSetSeriesFavorite,
                            onDeleteSeries = viewModel::onDeleteSeries,
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onDeleteComic = viewModel::onDeleteComic,
                            onQueryChanged = viewModel::onQueryChanged,
                            onSortSelected = viewModel::onSortSelected,
                            onOpenSeries = viewModel::onOpenSeries,
                        )
                        is Screen.Settings -> ComicSettingsScreen(comics = target.comics, onBack = { settingsOf = null })
                        Screen.AppSettings -> AppSettingsScreen(
                            scanning = state.scanning,
                            onFolderPicked = viewModel::onFolderPicked,
                            onRefresh = viewModel::onRefresh,
                            onOpenLicences = { licencesOpen = true },
                            onBack = { appSettingsOpen = false },
                        )
                        Screen.Licences -> LicencesScreen(onBack = { licencesOpen = false })
                        is Screen.Reader -> ReaderSession(target.request) {
                            ReaderScreen(
                                uri = target.request.uri,
                                initialPage = target.request.initialPage,
                                onPageChanged = { pageIndex, pageCount ->
                                    target.request.comicId?.let { viewModel.saveProgress(it, pageIndex, pageCount) }
                                },
                                onClose = { open = null },
                                onOpenNext = target.request.comicId
                                    ?.let { LibraryCatalog.nextInSeries(state.allComics, it) }
                                    ?.let { next -> { open = next.toOpenRequest() } },
                                initialAmbient = target.request.ambient,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Screen.key(): Any = when (this) {
    Screen.Onboarding -> "onboarding"
    Screen.Library -> "library"
    is Screen.Settings -> "settings-${comics.first().id}"
    Screen.AppSettings -> "app-settings"
    Screen.Licences -> "licences"
    is Screen.Reader -> request
}

@Composable
private fun ReaderSession(request: OpenRequest, content: @Composable () -> Unit) {
    val owner = remember(request) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private fun LibraryComic.toOpenRequest(): OpenRequest =
    OpenRequest(uri = documentUri.toUri(), comicId = id, initialPage = pageIndex, ambient = coverAmbient?.let { Color(it) })
