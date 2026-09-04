package com.comicify.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.BuildConfig
import com.comicify.R
import com.comicify.core.ui.BubbleScaleRow
import com.comicify.core.ui.SettingsChoiceRow
import com.comicify.core.ui.SettingsDivider
import com.comicify.core.ui.SettingsSection
import com.comicify.core.ui.SettingsSwitchRow
import com.comicify.core.ui.defaultLabel
import com.comicify.core.ui.labelRes
import com.comicify.core.ui.triStateOptions
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.openMode
import com.comicify.feature.library.domain.withOpenMode
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import com.comicify.feature.reader.domain.ReaderViewMode

private val SettingsCoverWidth = 96.dp
private val SettingsContentMaxWidth = 640.dp
private val ScreenPadding = 20.dp
private val SectionGap = 22.dp
private val ClearRowMinHeight = 56.dp

@Composable
fun ComicSettingsScreen(comics: List<LibraryComic>, onBack: () -> Unit) {
    val viewModel: ComicSettingsViewModel = hiltViewModel()
    LaunchedEffect(comics) { viewModel.show(comics) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val wholeSeries = comics.size > 1
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = SettingsContentMaxWidth + ScreenPadding * 2)
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            GhostAction(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), onClick = onBack)
            SettingsHeader(comics = comics)
            SettingsSection(
                eyebrow = stringResource(if (wholeSeries) R.string.settings_series_eyebrow else R.string.detail_settings_eyebrow),
                title = stringResource(R.string.detail_settings),
            ) {
                SettingsChoiceRow(
                    label = stringResource(R.string.settings_mode_on_open),
                    options = openModeOptions(defaults.guidedOnOpen),
                    selected = settings.openMode(),
                    onSelect = { viewModel.onSettingsChanged(settings.withOpenMode(it)) },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    label = stringResource(R.string.detail_setting_direction),
                    options = listOf(
                        null to defaultLabel(stringResource(defaults.direction.labelRes())),
                        ReadingDirection.LeftToRight to stringResource(R.string.detail_option_ltr),
                        ReadingDirection.RightToLeft to stringResource(R.string.detail_option_rtl),
                    ),
                    selected = settings.direction,
                    onSelect = { viewModel.onSettingsChanged(settings.copy(direction = it)) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = stringResource(R.string.detail_setting_cover_alone),
                    supporting = stringResource(R.string.detail_setting_cover_alone_detail),
                    checked = settings.coverAlone,
                    onCheckedChange = { viewModel.onSettingsChanged(settings.copy(coverAlone = it)) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    label = stringResource(R.string.detail_setting_split_wide_pages),
                    supporting = stringResource(R.string.detail_setting_split_wide_pages_detail),
                    checked = settings.splitWidePages,
                    onCheckedChange = { viewModel.onSettingsChanged(settings.copy(splitWidePages = it)) },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    label = stringResource(R.string.detail_setting_bubbles),
                    options = triStateOptions(defaults.bubblesOnOpen),
                    selected = settings.bubblesEnlarged,
                    onSelect = { enlarged ->
                        viewModel.onSettingsChanged(
                            settings.copy(bubblesEnlarged = enlarged, bubbleScale = settings.bubbleScale.takeIf { enlarged == true }),
                        )
                    },
                )
                if (settings.bubblesEnlarged == true) {
                    BubbleScaleRow(
                        label = stringResource(R.string.app_settings_bubble_scale),
                        scale = settings.bubbleScale ?: BUBBLE_ENLARGE_SCALE,
                        onScaleCommitted = { viewModel.onSettingsChanged(settings.copy(bubbleScale = it)) },
                    )
                }
                if (BuildConfig.DEBUG) {
                    SettingsDivider()
                    ClearDetectionsRow(onClearDetections = viewModel::onClearDetections)
                }
            }
        }
    }
}

@Composable
private fun openModeOptions(guidedOnOpen: Boolean): List<Pair<ReaderViewMode?, String>> {
    val defaultMode = if (guidedOnOpen) R.string.reader_mode_guided else R.string.reader_mode_pages
    return listOf(
        null to defaultLabel(stringResource(defaultMode)),
        ReaderViewMode.Pages to stringResource(R.string.reader_mode_pages),
        ReaderViewMode.Guided to stringResource(R.string.reader_mode_guided),
        ReaderViewMode.Strip to stringResource(R.string.reader_mode_strip),
    )
}

@Composable
private fun SettingsHeader(comics: List<LibraryComic>) {
    val comic = comics.first()
    val wholeSeries = comics.size > 1
    val glow = lerp(Ground, comic.ambientColor(), HeroGlowMix)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(glow, HeroGround)))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(SettingsCoverWidth).aspectRatio(2f / 3f).sharedCover(comic.id).clip(RoundedCornerShape(10.dp))) {
            CoverArt(comic = comic, showArtwork = true)
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!wholeSeries && comic.series != comic.title) {
                Text(
                    text = comic.series,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (wholeSeries) comic.series else comic.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (wholeSeries) {
                Text(
                    text = stringResource(R.string.settings_series_scope, comics.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentAmber,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ClearDetectionsRow(onClearDetections: () -> Unit) {
    var cleared by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ClearRowMinHeight)
            .clickable { onClearDetections(); cleared = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null, tint = if (cleared) Good else InkDim)
        Text(
            text = stringResource(if (cleared) R.string.detail_detections_cleared else R.string.detail_clear_detections),
            style = MaterialTheme.typography.bodyMedium,
            color = if (cleared) Good else InkDim,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}
