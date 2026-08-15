package com.comicify.feature.reader.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comicify.R
import com.comicify.core.window.ReadingPosture
import com.comicify.core.window.rememberReadingPosture
import com.comicify.feature.reader.data.PageLoader

private val InitialAmbient = Color(0xFF0B0B0F)

@Composable
fun ReaderScreen(uri: Uri, onClose: () -> Unit) {
    val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(uri))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val posture = rememberReadingPosture()

    var ambient by remember { mutableStateOf(InitialAmbient) }
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
        when {
            state.loading -> CenteredMessage(stringResource(R.string.reader_loading), showSpinner = true)
            state.error -> CenteredMessage(stringResource(R.string.reader_error_open))
            else -> viewModel.pageLoader?.let { loader ->
                ReaderSurface(
                    loader = loader,
                    posture = posture,
                    guided = state.guided,
                    guidedFullScreen = state.guidedFullScreen,
                    initialPage = state.position.pageIndex,
                    pendingJump = state.pendingJump,
                    onJumpApplied = viewModel::onJumpApplied,
                    onPageChanged = viewModel::onPageChanged,
                    onTap = viewModel::toggleChrome,
                    onAmbient = { ambient = it },
                )
            }
        }

        TopChrome(
            visible = state.chromeVisible,
            posture = posture,
            guided = state.guided,
            guidedFullScreen = state.guidedFullScreen,
            onToggleGuided = viewModel::toggleGuided,
            onToggleGuidedFullScreen = viewModel::toggleGuidedFullScreen,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomChrome(
            visible = state.chromeVisible,
            currentPage = state.position.pageIndex,
            pageCount = state.pageCount,
            scrubberLoader = if (state.guided) null else viewModel.pageLoader,
            onJumpToPage = viewModel::requestJump,
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
    onToggleGuided: () -> Unit,
    onToggleGuidedFullScreen: () -> Unit,
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
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (guided && posture == ReadingPosture.UnfoldedSpread) {
                    GuidedLayoutToggle(fullScreen = guidedFullScreen, onToggle = onToggleGuidedFullScreen)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                GuidedToggle(guided = guided, onToggle = onToggleGuided)
                Spacer(modifier = Modifier.width(10.dp))
                PostureChip(posture)
            }
        }
    }
}

@Composable
private fun GuidedToggle(guided: Boolean, onToggle: () -> Unit) {
    val background = if (guided) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
    IconButton(
        onClick = onToggle,
        modifier = Modifier.background(background, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Filled.ViewCarousel,
            contentDescription = stringResource(R.string.reader_action_guided),
            tint = Color.White,
        )
    }
}

@Composable
private fun GuidedLayoutToggle(fullScreen: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape),
    ) {
        Icon(
            imageVector = if (fullScreen) Icons.Filled.MenuBook else Icons.Filled.Fullscreen,
            contentDescription = stringResource(
                if (fullScreen) R.string.reader_action_guided_keep_spread else R.string.reader_action_guided_full_screen,
            ),
            tint = Color.White,
        )
    }
}

@Composable
private fun BottomChrome(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    scrubberLoader: PageLoader?,
    onJumpToPage: (Int) -> Unit,
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
        if (pageCount > 0) {
            ProgressBar(progress = readingProgress(currentPage, pageCount))
        }
    }
}

@Composable
private fun PostureChip(posture: ReadingPosture) {
    Text(
        text = stringResource(posture.labelRes()),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
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

private fun ReadingPosture.labelRes(): Int = when (this) {
    ReadingPosture.CompactSingle -> R.string.posture_folded
    ReadingPosture.UnfoldedSingle -> R.string.posture_unfolded
    ReadingPosture.UnfoldedSpread -> R.string.posture_spread
    ReadingPosture.Tabletop -> R.string.posture_tabletop
}
