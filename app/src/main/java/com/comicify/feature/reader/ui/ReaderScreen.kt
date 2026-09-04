package com.comicify.feature.reader.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
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
import com.comicify.feature.reader.domain.BUBBLE_SCALE_RANGE
import com.comicify.feature.reader.domain.BUBBLE_SCALE_STEP
import com.comicify.feature.reader.domain.ComicOpenError
import com.comicify.feature.reader.domain.ReaderViewMode
import com.comicify.feature.reader.domain.TapZone
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.roundToInt

private const val CHROME_SLIDE_MS = 200
private const val BACKDROP_DOWNSCALE = 4f
private const val TabularFigures = "tnum"
private const val BUBBLE_SCALE_DECIMALS = 10f
private val InitialAmbient = Color(0xFF0B0B0F)
private val NightTintColor = Color(0xFFFF8F00).copy(alpha = 0.16f)
private val PanelColor = Color(0xF0141416)
private val PanelHairlineColor = Color.White.copy(alpha = 0.08f)
private val PanelHairlineWidth = 1.dp
private val PanelShape = RoundedCornerShape(20.dp)
private val PanelRowShape = RoundedCornerShape(12.dp)
private val PanelWidth = 280.dp
private val PanelScreenMargin = 24.dp
private val PanelPadding = 8.dp
private val PanelRowHeight = 48.dp
private val PanelRowPadding = 12.dp
private val PanelRowGap = 12.dp
private val PanelIconSize = 24.dp
private val BubbleScaleValueWidth = 44.dp
private val MarkerDotSize = 8.dp
private val MarkerDotInset = 3.dp

@Composable
fun ReaderScreen(
    uri: Uri,
    onClose: () -> Unit,
    initialPage: Int = 0,
    comic: StripComic? = null,
    nextInSeries: (StripComic) -> StripComic? = { null },
    onPageChanged: (comicId: Long?, pageIndex: Int, pageCount: Int) -> Unit = { _, _, _ -> },
    onOpenIssue: (StripComic) -> Unit = {},
    initialAmbient: Color? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(application, uri, initialPage))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val windowState = rememberReadingWindowState()
    val posture = windowState.posture
    val pageTurnRequests = remember { MutableSharedFlow<PageTurnDirection>(extraBufferCapacity = 1) }

    RegisterVolumeKeyPageTurns(enabled = state.volumeKeyPagingEnabled) { direction ->
        pageTurnRequests.tryEmit(direction)
    }

    var activeComic by remember { mutableStateOf(comic) }
    var activeLoader by remember { mutableStateOf<PageLoader?>(null) }
    var activePageCount by remember { mutableIntStateOf(0) }
    val readingPageCount = if (state.verticalScroll && activePageCount > 0) activePageCount else state.pageCount

    LaunchedEffect(state.position.pageIndex, readingPageCount, activeComic) {
        if (readingPageCount > 0) onPageChanged(activeComic?.id, state.position.pageIndex, readingPageCount)
    }

    LaunchedEffect(viewModel) { viewModel.shareRequests.collect(context::startActivity) }
    var glitchDialogOpen by remember { mutableStateOf(false) }
    if (glitchDialogOpen) {
        GlitchReportDialog(
            onConfirm = { glitchDialogOpen = false; viewModel.reportGlitch(posture) },
            onDismiss = { glitchDialogOpen = false },
        )
    }

    var ambient by remember { mutableStateOf(initialAmbient ?: InitialAmbient) }
    var guidedIndex by remember { mutableIntStateOf(0) }
    var guidedCount by remember { mutableIntStateOf(1) }
    val glow by animateColorAsState(
        targetValue = lerp(Color.Black, ambient, 0.5f),
        animationSpec = tween(700),
        label = "ambient",
    )

    val atLastPage = readingPageCount > 0 && state.position.pageIndex >= readingPageCount - 1
    val nextIssue = activeComic?.let(nextInSeries)
    var atEnd by remember { mutableStateOf(false) }
    LaunchedEffect(atLastPage) { if (!atLastPage) atEnd = false }

    BackHandler { if (atEnd) atEnd = false else onClose() }

    val forwardSign = if (state.direction == ReadingDirection.LeftToRight) -1f else 1f
    val endOverscroll = rememberEndOverscroll(
        enabled = atLastPage && !state.guided && !(state.verticalScroll && nextIssue != null),
        forwardSign = forwardSign,
        vertical = state.verticalScroll,
    ) { atEnd = true }

    ImmersiveReadingMode(keepScreenOn = state.keepScreenOn)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(endOverscroll)
            .background(Color.Black),
    ) {
        AmbientBackdrop(glow)
        val openError = state.error
        when {
            state.loading -> CenteredMessage(stringResource(R.string.reader_loading), showSpinner = true)
            openError != null -> CenteredMessage(stringResource(openError.messageRes()))
            else -> viewModel.pageLoader?.let { loader ->
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    ReaderSurface(
                        loader = loader,
                        posture = posture,
                        hinge = windowState.hinge,
                        guided = state.guided,
                        guidedFullScreen = state.guidedFullScreen,
                        verticalScroll = state.verticalScroll,
                        comic = comic,
                        nextInSeries = nextInSeries,
                        openIssue = { viewModel.openChainIssue(it.uri) },
                        onStripActiveChanged = { issue, issueLoader, pageIndex, pageCount ->
                            activeComic = issue
                            activeLoader = issueLoader
                            activePageCount = pageCount
                            viewModel.onPageChanged(pageIndex)
                        },
                        onStripScrolled = viewModel::hideChrome,
                        bubbleScale = state.bubbleScale.takeIf { state.bubblesEnlarged },
                        direction = state.direction,
                        coverAlone = state.coverAlone,
                        initialPage = state.position.pageIndex,
                        pageTurnRequests = pageTurnRequests,
                        pendingJump = state.pendingJump,
                        onJumpApplied = viewModel::onJumpApplied,
                        onPageChanged = viewModel::onPageChanged,
                        onGuidedStop = { index, count -> guidedIndex = index; guidedCount = count },
                        onTap = { zone ->
                            when (zone) {
                                TapZone.Center -> viewModel.toggleChrome()
                                TapZone.Next -> pageTurnRequests.tryEmit(PageTurnDirection.Next)
                                TapZone.Previous -> pageTurnRequests.tryEmit(PageTurnDirection.Previous)
                            }
                        },
                        onAmbient = { ambient = it },
                    )
                }
            }
        }

        if (state.nightTintEnabled) {
            Box(modifier = Modifier.fillMaxSize().background(NightTintColor))
        }

        TopChrome(
            visible = state.chromeVisible,
            posture = posture,
            viewMode = ReaderViewMode.of(state.guided, state.verticalScroll),
            guidedFullScreen = state.guidedFullScreen,
            bubblesEnlarged = state.bubblesEnlarged,
            bubbleScale = state.bubbleScale,
            nightTintEnabled = state.nightTintEnabled,
            direction = state.direction,
            splitWidePages = state.splitWidePages,
            onViewMode = viewModel::setViewMode,
            onToggleBubblesEnlarged = viewModel::toggleBubblesEnlarged,
            onBubbleScale = viewModel::setBubbleScale,
            onToggleGuidedFullScreen = viewModel::toggleGuidedFullScreen,
            onToggleNightTint = viewModel::toggleNightTint,
            onToggleDirection = viewModel::toggleReadingDirection,
            onToggleSplitWidePages = viewModel::toggleSplitWidePages,
            onReportGlitch = { glitchDialogOpen = true },
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomChrome(
            visible = state.chromeVisible,
            currentPage = state.position.pageIndex,
            pageCount = readingPageCount,
            scrubberLoader = if (state.guided) null else activeLoader ?: viewModel.pageLoader,
            onJumpToPage = viewModel::requestJump,
            guided = state.guided,
            guidedStop = guidedIndex,
            guidedStopCount = guidedCount,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        EndOfComicOverlay(
            visible = atEnd,
            hasNext = nextIssue != null,
            onNext = { atEnd = false; nextIssue?.let(onOpenIssue) },
            onLibrary = onClose,
            onResume = { atEnd = false },
        )

        SplitSuggestionSnackbar(
            suggested = state.splitSuggested,
            onAccept = viewModel::acceptSplitSuggestion,
            onDismiss = viewModel::dismissSplitSuggestion,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@Composable
private fun SplitSuggestionSnackbar(
    suggested: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = remember { SnackbarHostState() }
    val message = stringResource(R.string.reader_split_suggestion)
    val action = stringResource(R.string.reader_split_suggestion_action)
    LaunchedEffect(suggested) {
        if (!suggested) return@LaunchedEffect
        val result = host.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onAccept() else onDismiss()
    }
    SnackbarHost(host, modifier = modifier)
}

private const val END_OVERSCROLL_TRIGGER = 160f

@Composable
private fun rememberEndOverscroll(
    enabled: Boolean,
    forwardSign: Float,
    vertical: Boolean,
    onReachedEnd: () -> Unit,
): NestedScrollConnection {
    val currentOnReachedEnd = rememberUpdatedState(onReachedEnd)
    return remember(enabled, forwardSign, vertical) {
        object : NestedScrollConnection {
            private var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled) {
                    pulled = 0f
                    return Offset.Zero
                }
                val forward = if (vertical) -available.y else available.x * forwardSign
                if (forward <= 0f) return Offset.Zero
                pulled += forward
                if (pulled >= END_OVERSCROLL_TRIGGER) {
                    pulled = 0f
                    currentOnReachedEnd.value()
                }
                return Offset.Zero
            }
        }
    }
}

@Composable
private fun EndOfComicOverlay(
    visible: Boolean,
    hasNext: Boolean,
    onNext: () -> Unit,
    onLibrary: () -> Unit,
    onResume: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(indication = null, interactionSource = null, onClick = onResume),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.reader_end_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (hasNext) {
                        Button(onClick = onNext, modifier = Modifier.width(240.dp)) {
                            Text(text = stringResource(R.string.reader_end_next))
                        }
                    }
                    TextButton(onClick = onLibrary, modifier = Modifier.width(240.dp)) {
                        Text(text = stringResource(R.string.reader_end_library))
                    }
                    TextButton(onClick = onResume, modifier = Modifier.width(240.dp)) {
                        Text(
                            text = stringResource(R.string.reader_end_resume),
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopChrome(
    visible: Boolean,
    posture: ReadingPosture,
    viewMode: ReaderViewMode,
    guidedFullScreen: Boolean,
    bubblesEnlarged: Boolean,
    bubbleScale: Float,
    nightTintEnabled: Boolean,
    direction: ReadingDirection,
    splitWidePages: Boolean,
    onViewMode: (ReaderViewMode) -> Unit,
    onToggleBubblesEnlarged: () -> Unit,
    onBubbleScale: (Float) -> Unit,
    onToggleGuidedFullScreen: () -> Unit,
    onToggleNightTint: () -> Unit,
    onToggleDirection: () -> Unit,
    onToggleSplitWidePages: () -> Unit,
    onReportGlitch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openPanel by remember { mutableStateOf<HudPanelKind?>(null) }
    SlidingChrome(visible = visible, slideSign = -1f, modifier = modifier) {
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    CircleControl(
                        icon = Icons.Filled.Visibility,
                        open = openPanel == HudPanelKind.ViewMode,
                        marked = viewMode != ReaderViewMode.Pages || bubblesEnlarged,
                        contentDescription = stringResource(R.string.reader_action_view_mode),
                        onClick = { openPanel = openPanel.toggled(HudPanelKind.ViewMode) },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    CircleControl(
                        icon = Icons.Filled.Settings,
                        open = openPanel == HudPanelKind.Settings,
                        marked = nightTintEnabled || direction == ReadingDirection.RightToLeft || splitWidePages,
                        contentDescription = stringResource(R.string.reader_action_settings),
                        onClick = { openPanel = openPanel.toggled(HudPanelKind.Settings) },
                    )
                }
                RevealedPanel(visible = openPanel == HudPanelKind.ViewMode) {
                    ViewModePanel(
                        mode = viewMode,
                        bubblesEnlarged = bubblesEnlarged,
                        bubbleScale = bubbleScale,
                        onMode = { openPanel = null; onViewMode(it) },
                        onToggleBubbles = onToggleBubblesEnlarged,
                        onBubbleScale = onBubbleScale,
                    )
                }
                RevealedPanel(visible = openPanel == HudPanelKind.Settings) {
                    ReaderSettingsPanel(
                        showGuidedLayout = viewMode == ReaderViewMode.Guided && posture == ReadingPosture.UnfoldedSpread,
                        guidedFullScreen = guidedFullScreen,
                        nightTintEnabled = nightTintEnabled,
                        direction = direction,
                        splitWidePages = splitWidePages,
                        onToggleGuidedFullScreen = onToggleGuidedFullScreen,
                        onToggleNightTint = onToggleNightTint,
                        onToggleDirection = onToggleDirection,
                        onToggleSplitWidePages = onToggleSplitWidePages,
                        onReportGlitch = { openPanel = null; onReportGlitch() },
                    )
                }
            }
        }
    }
}

private enum class HudPanelKind { ViewMode, Settings }

private fun HudPanelKind?.toggled(kind: HudPanelKind): HudPanelKind? = if (this == kind) null else kind

private fun Float.steppedBy(delta: Float): Float = ((this + delta) * BUBBLE_SCALE_DECIMALS).roundToInt() / BUBBLE_SCALE_DECIMALS

@Composable
private fun RevealedPanel(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        content = { content() },
    )
}

@Composable
private fun HudPanel(content: @Composable ColumnScope.() -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Column(
        modifier = Modifier
            .width(min(PanelWidth, screenWidth - PanelScreenMargin))
            .clip(PanelShape)
            .background(PanelColor)
            .border(PanelHairlineWidth, PanelHairlineColor, PanelShape)
            .padding(PanelPadding),
        content = content,
    )
}

@Composable
private fun PanelRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    labelColor: Color = Color.White,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelRowHeight)
            .clip(PanelRowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = PanelRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PanelRowGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(PanelIconSize),
        )
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = PanelRowPadding, vertical = PanelPadding),
        thickness = PanelHairlineWidth,
        color = PanelHairlineColor,
    )
}

@Composable
private fun ViewModePanel(
    mode: ReaderViewMode,
    bubblesEnlarged: Boolean,
    bubbleScale: Float,
    onMode: (ReaderViewMode) -> Unit,
    onToggleBubbles: () -> Unit,
    onBubbleScale: (Float) -> Unit,
) {
    HudPanel {
        ViewModeChoice(Icons.Filled.CropPortrait, R.string.reader_mode_pages, mode == ReaderViewMode.Pages) {
            onMode(ReaderViewMode.Pages)
        }
        ViewModeChoice(Icons.Filled.ViewCarousel, R.string.reader_mode_guided, mode == ReaderViewMode.Guided) {
            onMode(ReaderViewMode.Guided)
        }
        ViewModeChoice(Icons.Filled.ViewDay, R.string.reader_mode_strip, mode == ReaderViewMode.Strip) {
            onMode(ReaderViewMode.Strip)
        }
        if (mode.allowsBubbles()) {
            PanelDivider()
            PanelRow(
                icon = Icons.Filled.ChatBubbleOutline,
                label = stringResource(R.string.reader_mode_bubbles),
                onClick = onToggleBubbles,
            ) {
                Switch(checked = bubblesEnlarged, onCheckedChange = null)
            }
            RevealedPanel(visible = bubblesEnlarged) {
                BubbleScaleStepper(scale = bubbleScale, onScale = onBubbleScale)
            }
        }
    }
}

@Composable
private fun ViewModeChoice(icon: ImageVector, labelRes: Int, selected: Boolean, onSelect: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    PanelRow(
        icon = icon,
        label = stringResource(labelRes),
        onClick = onSelect,
        labelColor = if (selected) accent else Color.White,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(PanelIconSize),
            )
        }
    }
}

@Composable
private fun BubbleScaleStepper(scale: Float, onScale: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelRowHeight)
            .padding(horizontal = PanelRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        BubbleScaleStep(
            icon = Icons.Filled.Remove,
            contentDescription = stringResource(R.string.reader_bubble_scale_smaller),
            enabled = scale > BUBBLE_SCALE_RANGE.start,
            onClick = { onScale(scale.steppedBy(-BUBBLE_SCALE_STEP)) },
        )
        Text(
            text = "%.1f×".format(scale),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularFigures),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(BubbleScaleValueWidth),
        )
        BubbleScaleStep(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.reader_bubble_scale_bigger),
            enabled = scale < BUBBLE_SCALE_RANGE.endInclusive,
            onClick = { onScale(scale.steppedBy(BUBBLE_SCALE_STEP)) },
        )
    }
}

@Composable
private fun BubbleScaleStep(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
        enabled = enabled,
        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun ReaderSettingsPanel(
    showGuidedLayout: Boolean,
    guidedFullScreen: Boolean,
    nightTintEnabled: Boolean,
    direction: ReadingDirection,
    splitWidePages: Boolean,
    onToggleGuidedFullScreen: () -> Unit,
    onToggleNightTint: () -> Unit,
    onToggleDirection: () -> Unit,
    onToggleSplitWidePages: () -> Unit,
    onReportGlitch: () -> Unit,
) {
    HudPanel {
        PanelRow(
            icon = Icons.Filled.NightsStay,
            label = stringResource(R.string.reader_action_night_tint),
            onClick = onToggleNightTint,
        ) {
            Switch(checked = nightTintEnabled, onCheckedChange = null)
        }
        PanelRow(
            icon = Icons.Filled.SwapHoriz,
            label = stringResource(R.string.reader_setting_direction),
            onClick = onToggleDirection,
        ) {
            Text(
                text = stringResource(
                    if (direction == ReadingDirection.RightToLeft) R.string.reader_direction_rtl else R.string.reader_direction_ltr,
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        PanelRow(
            icon = Icons.Filled.VerticalSplit,
            label = stringResource(R.string.reader_action_split_wide_pages),
            onClick = onToggleSplitWidePages,
        ) {
            Switch(checked = splitWidePages, onCheckedChange = null)
        }
        if (showGuidedLayout) {
            PanelRow(
                icon = Icons.Filled.Fullscreen,
                label = stringResource(R.string.reader_action_guided_full_screen),
                onClick = onToggleGuidedFullScreen,
            ) {
                Switch(checked = guidedFullScreen, onCheckedChange = null)
            }
        }
        PanelDivider()
        PanelRow(
            icon = Icons.Filled.BugReport,
            label = stringResource(R.string.reader_action_report_glitch),
            onClick = onReportGlitch,
        )
    }
}

@Composable
private fun GlitchReportDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.glitch_report_title)) },
        text = { Text(stringResource(R.string.glitch_report_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.glitch_report_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.glitch_report_cancel)) } },
    )
}

@Composable
private fun CircleControl(
    icon: ImageVector,
    open: Boolean,
    marked: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier.background(
                if (open) accent else Color.White.copy(alpha = 0.12f),
                CircleShape,
            ),
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White)
        }
        if (marked && !open) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -MarkerDotInset, y = MarkerDotInset)
                    .size(MarkerDotSize)
                    .background(accent, CircleShape),
            )
        }
    }
}

@Composable
private fun AmbientBackdrop(glow: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize(1f / BACKDROP_DOWNSCALE)
            .graphicsLayer {
                scaleX = BACKDROP_DOWNSCALE
                scaleY = BACKDROP_DOWNSCALE
                transformOrigin = TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow, Color.Black),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
    )
}

@Composable
private fun SlidingChrome(
    visible: Boolean,
    slideSign: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shown by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(CHROME_SLIDE_MS), label = "chrome")
    Box(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            alpha = shown
            translationY = slideSign * size.height * (1f - shown)
        },
    ) {
        content()
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
        SlidingChrome(visible = visible, slideSign = 1f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (scrubberLoader != null && pageCount > 0) {
                    ThumbnailScrubber(
                        loader = scrubberLoader,
                        visible = visible,
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
private fun ImmersiveReadingMode(keepScreenOn: Boolean) {
    val activity = LocalContext.current as Activity
    DisposableEffect(keepScreenOn) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun ComicOpenError.messageRes(): Int = when (this) {
    ComicOpenError.UnsupportedFormat -> R.string.reader_error_unsupported_format
    ComicOpenError.EmptyArchive -> R.string.reader_error_empty_archive
    ComicOpenError.ReadFailure -> R.string.reader_error_read_failure
    ComicOpenError.PasswordProtected -> R.string.reader_error_password_protected
    ComicOpenError.AccessLost -> R.string.reader_error_access_lost
}
