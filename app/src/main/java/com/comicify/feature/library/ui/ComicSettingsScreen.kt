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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.BuildConfig
import com.comicify.R
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.library.domain.ComicSettings
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import com.comicify.feature.reader.domain.BUBBLE_SCALE_RANGE

private val SettingsCoverWidth = 96.dp
private val BubbleScaleLabelWidth = 44.dp
private val BubbleScaleSliderWidth = 240.dp
private const val BUBBLE_SCALE_STEPS = 8

@Composable
fun ComicSettingsScreen(comics: List<LibraryComic>, onBack: () -> Unit) {
    val viewModel: ComicSettingsViewModel = hiltViewModel()
    LaunchedEffect(comics) { viewModel.show(comics) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GhostAction(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), onClick = onBack)
        SettingsHeader(comics = comics)
        SectionHeader(eyebrow = stringResource(R.string.detail_settings_eyebrow), title = stringResource(R.string.detail_settings))
        SettingRow(label = stringResource(R.string.detail_setting_direction)) {
            OptionChips(
                options = listOf(
                    null to R.string.detail_option_default,
                    ReadingDirection.LeftToRight to R.string.detail_option_ltr,
                    ReadingDirection.RightToLeft to R.string.detail_option_rtl,
                ),
                selected = settings.direction,
                onSelect = { viewModel.onSettingsChanged(settings.copy(direction = it)) },
            )
        }
        SettingRow(label = stringResource(R.string.detail_setting_cover_alone)) {
            OptionChips(
                options = listOf(false to R.string.detail_option_off, true to R.string.detail_option_on),
                selected = settings.coverAlone,
                onSelect = { viewModel.onSettingsChanged(settings.copy(coverAlone = it)) },
            )
        }
        SettingRow(label = stringResource(R.string.detail_setting_bubbles)) {
            TriStateChips(
                selected = settings.bubblesEnlarged,
                onSelect = { viewModel.onSettingsChanged(settings.copy(bubblesEnlarged = it, bubbleScale = settings.bubbleScale.takeIf { _ -> it == true })) },
            )
            if (settings.bubblesEnlarged == true) {
                BubbleScaleRow(
                    scale = settings.bubbleScale ?: BUBBLE_ENLARGE_SCALE,
                    onScaleCommitted = { viewModel.onSettingsChanged(settings.copy(bubbleScale = it)) },
                )
            }
        }
        SettingRow(label = stringResource(R.string.detail_setting_guided)) {
            TriStateChips(selected = settings.guided, onSelect = { viewModel.onSettingsChanged(settings.copy(guided = it)) })
        }
        if (BuildConfig.DEBUG) ClearDetectionsRow(onClearDetections = viewModel::onClearDetections)
    }
}

@Composable
private fun SettingsHeader(comics: List<LibraryComic>) {
    val comic = comics.first()
    val wholeSeries = comics.size > 1
    val glow = lerp(Color.Black, comic.ambientColor(), HeroGlowMix)
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
private fun BubbleScaleRow(scale: Float, onScaleCommitted: (Float) -> Unit) {
    var dragged by remember(scale) { mutableStateOf(scale) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "%.1f×".format(dragged),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(BubbleScaleLabelWidth),
        )
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onScaleCommitted(dragged) },
            valueRange = BUBBLE_SCALE_RANGE,
            steps = BUBBLE_SCALE_STEPS,
            modifier = Modifier.width(BubbleScaleSliderWidth),
        )
    }
}

@Composable
private fun SettingRow(label: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        options()
    }
}

@Composable
private fun TriStateChips(selected: Boolean?, onSelect: (Boolean?) -> Unit) {
    OptionChips(
        options = listOf(null to R.string.detail_option_default, true to R.string.detail_option_on, false to R.string.detail_option_off),
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> OptionChips(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, labelRes) ->
            val active = value == selected
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Color.White else InkDim,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Accent else Surface2)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ClearDetectionsRow(onClearDetections: () -> Unit) {
    var cleared by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClearDetections(); cleared = true }
            .padding(vertical = 8.dp),
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
