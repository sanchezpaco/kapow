package com.comicify.feature.reader.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comicify.R
import com.comicify.core.input.PageTurnDirection
import com.comicify.core.input.RegisterVolumeKeyPageTurns
import com.comicify.core.window.ReadingPosture
import com.comicify.core.window.rememberReadingWindowState
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.ComicOpenError
import kotlinx.coroutines.flow.MutableSharedFlow

private val InitialAmbient = Color(0xFF0B0B0F)
private val NightTintColor = Color(0xFFFF8F00).copy(alpha = 0.16f)

@Composable
fun ReaderScreen(
    uri: Uri,
    onClose: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (pageIndex: Int, pageCount: Int) -> Unit = { _, _ -> },
) {
    val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(uri, initialPage))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val windowState = rememberReadingWindowState()
    val posture = windowState.posture
    val pageTurnRequests = remember { MutableSharedFlow<PageTurnDirection>(extraBufferCapacity = 1) }

    RegisterVolumeKeyPageTurns(enabled = state.volumeKeyPagingEnabled) { direction ->
        pageTurnRequests.tryEmit(direction)
    }

    LaunchedEffect(state.position.pageIndex, state.pageCount) {
        if (state.pageCount > 0) onPageChanged(state.position.pageIndex, state.pageCount)
    }

    var ambient by remember { mutableStateOf(InitialAmbient) }
    var guidedIndex by remember { mutableIntStateOf(0) }
    var guidedCount by remember { mutableIntStateOf(1) }
    val glow by animateColorAsState(
        targetValue = lerp(Color.Black, ambient, 0.5f),
        animationSpec = tween(700),
        label = "ambient",
    )

    ImmersiveReadingMode()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color.Black)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow, Color.Black),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
    ) {
        val openError = state.error
        when {
            state.loading -> CenteredMessage(stringResource(R.string.reader_loading), showSpinner = true)
            openError != null -> CenteredMessage(stringResource(openError.messageRes()))
            else -> viewModel.pageLoader?.let { loader ->
                ReaderSurface(
                    loader = loader,
                    posture = posture,
                    hinge = windowState.hinge,
                    guided = state.guided,
                    guidedFullScreen = state.guidedFullScreen,
                    direction = state.direction,
                    initialPage = state.position.pageIndex,
                    pageTurnRequests = pageTurnRequests,
                    pendingJump = state.pendingJump,
                    onJumpApplied = viewModel::onJumpApplied,
                    onPageChanged = viewModel::onPageChanged,
                    onGuidedStop = { index, count -> guidedIndex = index; guidedCount = count },
                    onTap = viewModel::toggleChrome,
                    onAmbient = { ambient = it },
                )
            }
        }

        if (state.nightTintEnabled) {
            Box(modifier = Modifier.fillMaxSize().background(NightTintColor))
        }

        TopChrome(
            visible = state.chromeVisible,
            posture = posture,
            guided = state.guided,
            guidedFullScreen = state.guidedFullScreen,
            nightTintEnabled = state.nightTintEnabled,
            direction = state.direction,
            onToggleGuided = viewModel::toggleGuided,
            onToggleGuidedFullScreen = viewModel::toggleGuidedFullScreen,
            onToggleNightTint = viewModel::toggleNightTint,
            onToggleDirection = viewModel::toggleReadingDirection,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomChrome(
            visible = state.chromeVisible,
            currentPage = state.position.pageIndex,
            pageCount = state.pageCount,
            scrubberLoader = if (state.guided) null else viewModel.pageLoader,
            onJumpToPage = viewModel::requestJump,
            guided = state.guided,
            guidedStop = guidedIndex,
            guidedStopCount = guidedCount,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopChrome(
    visible: Boolean,
    posture: ReadingPosture,
    guided: Boolean,
    guidedFullScreen: Boolean,
    nightTintEnabled: Boolean,
    direction: ReadingDirection,
    onToggleGuided: () -> Unit,
    onToggleGuidedFullScreen: () -> Unit,
    onToggleNightTint: () -> Unit,
    onToggleDirection: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_action_close),
                    tint = Color.White,
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                GuidedToggle(guided = guided, onToggle = onToggleGuided)
                Spacer(modifier = Modifier.width(10.dp))
                ReaderSettingsMenu(
                    showGuidedLayout = guided && posture == ReadingPosture.UnfoldedSpread,
                    guidedFullScreen = guidedFullScreen,
                    nightTintEnabled = nightTintEnabled,
                    direction = direction,
                    onToggleGuidedFullScreen = onToggleGuidedFullScreen,
                    onToggleNightTint = onToggleNightTint,
                    onToggleDirection = onToggleDirection,
                )
            }
        }
    }
}

@Composable
private fun GuidedToggle(guided: Boolean, onToggle: () -> Unit) {
    CircleControl(
        icon = Icons.Filled.ViewCarousel,
        active = guided,
        contentDescription = stringResource(R.string.reader_action_guided),
        onClick = onToggle,
    )
}

@Composable
private fun ReaderSettingsMenu(
    showGuidedLayout: Boolean,
    guidedFullScreen: Boolean,
    nightTintEnabled: Boolean,
    direction: ReadingDirection,
    onToggleGuidedFullScreen: () -> Unit,
    onToggleNightTint: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircleControl(
            icon = Icons.Filled.Settings,
            active = expanded,
            contentDescription = stringResource(R.string.reader_action_settings),
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showGuidedLayout) {
                    CircleControl(
                        icon = if (guidedFullScreen) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Fullscreen,
                        active = guidedFullScreen,
                        contentDescription = stringResource(
                            if (guidedFullScreen) R.string.reader_action_guided_keep_spread else R.string.reader_action_guided_full_screen,
                        ),
                        onClick = onToggleGuidedFullScreen,
                    )
                }
                CircleControl(
                    icon = Icons.Filled.NightsStay,
                    active = nightTintEnabled,
                    contentDescription = stringResource(R.string.reader_action_night_tint),
                    onClick = onToggleNightTint,
                )
                CircleControl(
                    icon = Icons.Filled.SwapHoriz,
                    active = direction == ReadingDirection.RightToLeft,
                    contentDescription = stringResource(
                        if (direction == ReadingDirection.RightToLeft) R.string.reader_action_direction_rtl else R.string.reader_action_direction_ltr,
                    ),
                    onClick = onToggleDirection,
                )
            }
        }
    }
}

@Composable
private fun CircleControl(icon: ImageVector, active: Boolean, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(
            if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
            CircleShape,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun BottomChrome(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    scrubberLoader: PageLoader?,
    onJumpToPage: (Int) -> Unit,
    guided: Boolean,
    guidedStop: Int,
    guidedStopCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (scrubberLoader != null && pageCount > 0) {
                    ThumbnailScrubber(
                        loader = scrubberLoader,
                        currentPage = currentPage,
                        pageCount = pageCount,
                        onSelect = onJumpToPage,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (pageCount > 0) {
                    PageCounter(
                        current = currentPage,
                        total = pageCount,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        }
        if (guided && guidedStopCount > 1) {
            GuidedStops(
                current = guidedStop,
                count = guidedStopCount,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (pageCount > 0) {
            ProgressBar(progress = readingProgress(currentPage, pageCount))
        }
    }
}

@Composable
private fun CenteredMessage(text: String, showSpinner: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ImmersiveReadingMode() {
    val activity = LocalContext.current as Activity
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private fun ComicOpenError.messageRes(): Int = when (this) {
    ComicOpenError.UnsupportedFormat -> R.string.reader_error_unsupported_format
    ComicOpenError.EmptyArchive -> R.string.reader_error_empty_archive
    ComicOpenError.ReadFailure -> R.string.reader_error_read_failure
}
