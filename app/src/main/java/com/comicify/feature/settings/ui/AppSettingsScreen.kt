package com.comicify.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.BuildConfig
import com.comicify.R
import com.comicify.core.ui.BubbleScaleRow
import com.comicify.core.ui.OnOffChips
import com.comicify.core.ui.OptionChips
import com.comicify.core.ui.SettingRow
import com.comicify.core.ui.theme.ComicifyPalette
import com.comicify.core.ui.theme.ComicifyTheme
import com.comicify.core.ui.theme.ThemeAccent
import com.comicify.core.ui.theme.ThemeChoice
import com.comicify.core.ui.theme.ThemeGround
import com.comicify.core.ui.theme.resolve
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.library.ui.GhostAction
import com.comicify.feature.library.ui.PrimaryAction
import com.comicify.feature.library.ui.SectionHeader

private val SettingsContentMaxWidth = 640.dp
private val SwatchSize = 44.dp
private val SwatchRingWidth = 2.5.dp
private val SwatchRingGap = 3.dp
private val SwatchOutlineWidth = 1.dp

@Composable
fun AppSettingsScreen(scanning: Boolean, onFolderPicked: (Uri) -> Unit, onRefresh: () -> Unit, onOpenLicences: () -> Unit, onBack: () -> Unit) {
    val viewModel: AppSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onFolderPicked) }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .widthIn(max = SettingsContentMaxWidth),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GhostAction(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), onClick = onBack)
        Text(
            text = stringResource(R.string.app_settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ReadingSection(state, viewModel)
        ScreenSection(state, viewModel)
        LibrarySection(state.folderUri, scanning, onPickFolder = { folderLauncher.launch(null) }, onRefresh = onRefresh)
        AppearanceSection(state.theme, onThemeSelected = viewModel::onThemeSelected)
        AboutSection(onReplayOnboarding = viewModel::onReplayOnboarding, onOpenLicences = onOpenLicences)
    }
}

@Composable
private fun ReadingSection(state: AppSettingsUiState, viewModel: AppSettingsViewModel) {
    SectionHeader(eyebrow = stringResource(R.string.app_settings_defaults_eyebrow), title = stringResource(R.string.app_settings_reading))
    SettingRow(label = stringResource(R.string.detail_setting_direction)) {
        OptionChips(
            options = listOf(
                ReadingDirection.LeftToRight to stringResource(R.string.detail_option_ltr),
                ReadingDirection.RightToLeft to stringResource(R.string.detail_option_rtl),
            ),
            selected = state.direction,
            onSelect = viewModel::onDirectionSelected,
        )
    }
    SettingRow(label = stringResource(R.string.detail_setting_bubbles)) {
        OnOffChips(selected = state.bubblesOnOpen, onSelect = viewModel::onBubblesOnOpenChanged)
    }
    SettingRow(label = stringResource(R.string.app_settings_bubble_scale)) {
        BubbleScaleRow(scale = state.bubbleScale, onScaleCommitted = viewModel::onBubbleScaleChanged)
    }
    SettingRow(label = stringResource(R.string.detail_setting_guided)) {
        OnOffChips(selected = state.guidedOnOpen, onSelect = viewModel::onGuidedOnOpenChanged)
    }
    SettingRow(label = stringResource(R.string.app_settings_volume_keys)) {
        OnOffChips(selected = state.volumeKeyPageTurn, onSelect = viewModel::onVolumeKeyPageTurnChanged)
    }
}

@Composable
private fun ScreenSection(state: AppSettingsUiState, viewModel: AppSettingsViewModel) {
    SectionHeader(eyebrow = stringResource(R.string.app_settings_reader_eyebrow), title = stringResource(R.string.app_settings_screen))
    SettingRow(label = stringResource(R.string.reader_action_night_tint)) {
        OnOffChips(selected = state.nightTint, onSelect = viewModel::onNightTintChanged)
    }
    SettingRow(label = stringResource(R.string.app_settings_keep_screen_on)) {
        OnOffChips(selected = state.keepScreenOn, onSelect = viewModel::onKeepScreenOnChanged)
    }
}

@Composable
private fun LibrarySection(folderUri: String?, scanning: Boolean, onPickFolder: () -> Unit, onRefresh: () -> Unit) {
    val palette = ComicifyTheme.palette
    SectionHeader(eyebrow = stringResource(R.string.app_settings_collection_eyebrow), title = stringResource(R.string.library_title))
    SettingRow(label = stringResource(R.string.app_settings_folder)) {
        Text(
            text = folderUri?.let(::folderDisplayName) ?: stringResource(R.string.app_settings_no_folder),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryAction(icon = Icons.Outlined.CreateNewFolder, label = stringResource(R.string.library_pick_folder), onClick = onPickFolder)
            if (folderUri != null) RescanAction(scanning = scanning, onRefresh = onRefresh)
        }
    }
}

@Composable
private fun RescanAction(scanning: Boolean, onRefresh: () -> Unit) {
    val palette = ComicifyTheme.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.raised)
            .clickable(enabled = !scanning, onClick = onRefresh)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.accent)
        } else {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(18.dp))
        }
        Text(
            text = stringResource(if (scanning) R.string.library_scanning else R.string.library_refresh),
            color = palette.inkDim,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun folderDisplayName(treeUri: String): String =
    Uri.decode(treeUri).substringAfterLast(':').substringAfterLast('/').ifEmpty { treeUri }

@Composable
private fun AppearanceSection(theme: ThemeChoice, onThemeSelected: (ThemeChoice) -> Unit) {
    SectionHeader(eyebrow = stringResource(R.string.app_settings_theme_eyebrow), title = stringResource(R.string.app_settings_appearance))
    SettingRow(label = stringResource(R.string.app_settings_ground)) {
        OptionChips(
            options = listOf(
                ThemeGround.Black to stringResource(R.string.app_settings_ground_black),
                ThemeGround.Graphite to stringResource(R.string.app_settings_ground_graphite),
                ThemeGround.Paper to stringResource(R.string.app_settings_ground_paper),
            ),
            selected = theme.ground,
            onSelect = { onThemeSelected(theme.copy(ground = it)) },
        )
    }
    SettingRow(label = stringResource(R.string.app_settings_accent)) {
        val context = LocalContext.current
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeAccent.entries.forEach { accent ->
                ThemeSwatch(
                    ground = theme.ground.background,
                    accent = ComicifyPalette.of(theme.ground, accent.resolve(context)).accent,
                    dynamic = accent == ThemeAccent.Dynamic,
                    selected = accent == theme.accent,
                    contentDescription = stringResource(accent.labelRes()),
                    onClick = { onThemeSelected(theme.copy(accent = accent)) },
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatch(ground: Color, accent: Color, dynamic: Boolean, selected: Boolean, contentDescription: String, onClick: () -> Unit) {
    val ring = ComicifyTheme.palette.inkDim
    val outline = ComicifyTheme.palette.track
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringWidth = SwatchRingWidth.toPx()
            val inset = if (selected) ringWidth + SwatchRingGap.toPx() else 0f
            val radius = size.minDimension / 2f - inset
            val centre = center
            drawCircle(color = ground, radius = radius, center = centre)
            drawPath(path = lowerRightHalf(centre, radius), color = accent)
            drawCircle(color = outline, radius = radius - SwatchOutlineWidth.toPx() / 2f, center = centre, style = Stroke(SwatchOutlineWidth.toPx()))
            if (selected) drawCircle(color = ring, radius = size.minDimension / 2f - ringWidth / 2f, style = Stroke(ringWidth))
        }
        if (dynamic) Icon(imageVector = Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

private fun lowerRightHalf(centre: Offset, radius: Float): Path = Path().apply {
    moveTo(centre.x - radius, centre.y + radius)
    lineTo(centre.x + radius, centre.y - radius)
    arcTo(
        rect = Rect(centre - Offset(radius, radius), Size(radius * 2, radius * 2)),
        startAngleDegrees = -45f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    close()
}

private fun ThemeAccent.labelRes(): Int = when (this) {
    ThemeAccent.Red -> R.string.app_settings_accent_red
    ThemeAccent.Amber -> R.string.app_settings_accent_amber
    ThemeAccent.Orange -> R.string.app_settings_accent_orange
    ThemeAccent.Green -> R.string.app_settings_accent_green
    ThemeAccent.Cyan -> R.string.app_settings_accent_cyan
    ThemeAccent.Violet -> R.string.app_settings_accent_violet
    ThemeAccent.Pink -> R.string.app_settings_accent_pink
    ThemeAccent.Dynamic -> R.string.app_settings_accent_dynamic
}

@Composable
private fun AboutSection(onReplayOnboarding: () -> Unit, onOpenLicences: () -> Unit) {
    val palette = ComicifyTheme.palette
    SectionHeader(eyebrow = stringResource(R.string.app_name), title = stringResource(R.string.app_settings_about))
    Text(
        text = stringResource(R.string.app_settings_version, BuildConfig.VERSION_NAME, BuildConfig.BUILD_LABEL),
        style = MaterialTheme.typography.bodyMedium,
        color = palette.inkDim,
    )
    AboutAction(label = stringResource(R.string.app_settings_replay_onboarding), onClick = onReplayOnboarding)
    AboutAction(label = stringResource(R.string.app_settings_licences), onClick = onOpenLicences)
}

@Composable
private fun AboutAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = ComicifyTheme.palette.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
