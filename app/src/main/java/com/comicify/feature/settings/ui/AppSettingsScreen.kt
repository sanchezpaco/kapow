package com.comicify.feature.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.BuildConfig
import com.comicify.R
import com.comicify.core.ui.BubbleScaleRow
import com.comicify.core.ui.SettingsActionRow
import com.comicify.core.ui.SettingsChoiceRow
import com.comicify.core.ui.SettingsDivider
import com.comicify.core.ui.SettingsNote
import com.comicify.core.ui.SettingsSection
import com.comicify.core.ui.SettingsStackedRow
import com.comicify.core.ui.SettingsSwitchRow
import com.comicify.core.ui.folderDisplayName
import com.comicify.core.ui.theme.KapowPalette
import com.comicify.core.ui.theme.KapowTheme
import com.comicify.core.ui.theme.ThemeAccent
import com.comicify.core.ui.theme.ThemeChoice
import com.comicify.core.ui.theme.ThemeGround
import com.comicify.core.ui.theme.resolve
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.library.ui.GhostAction

private val ScreenPadding = 20.dp
private val SectionGap = 22.dp
private val ColumnGap = 20.dp
private val SingleColumnMaxWidth = 640.dp
private val SideColumnMaxWidth = 480.dp
private val TwoColumnMinWidth = 840.dp
private val SwatchSize = 44.dp
private val SwatchRingWidth = 2.5.dp
private val SwatchRingGap = 3.dp
private val SwatchOutlineWidth = 1.dp
private val GroundTileWidth = 76.dp
private val GroundTileHeight = 48.dp
private val GroundTileCorner = 12.dp
private val GroundTileRingWidth = 2.dp
private val GroundTileDot = 10.dp
private val GroundTileGap = 10.dp
private val ActionCorner = 12.dp
private val ActionPaddingHorizontal = 16.dp
private val ActionPaddingVertical = 11.dp
private val ActionIconSize = 18.dp
private val ActionGap = 10.dp

@Composable
fun AppSettingsScreen(
    scanning: Boolean,
    scrollState: ScrollState,
    onFolderPicked: (Uri) -> Unit,
    onRefresh: () -> Unit,
    onOpenLicences: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: AppSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onFolderPicked) }
    BackHandler(onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scrollState)) {
        val twoColumns = maxWidth >= TwoColumnMinWidth
        val contentWidth = if (twoColumns) SideColumnMaxWidth * 2 + ColumnGap else SingleColumnMaxWidth
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = contentWidth + ScreenPadding * 2)
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            GhostAction(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), onClick = onBack)
            Text(
                text = stringResource(R.string.app_settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val readerSections: @Composable () -> Unit = {
                ReadingSection(state, viewModel)
                ScreenSection(state, viewModel)
            }
            val appSections: @Composable () -> Unit = {
                LibrarySection(state.folderUri, scanning, onPickFolder = { folderLauncher.launch(null) }, onRefresh = onRefresh)
                AppearanceSection(state.theme, onThemeSelected = viewModel::onThemeSelected)
                AboutSection(onReplayOnboarding = viewModel::onReplayOnboarding, onOpenLicences = onOpenLicences)
            }
            if (twoColumns) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SectionGap)) { readerSections() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SectionGap)) { appSections() }
                }
            } else {
                readerSections()
                appSections()
            }
        }
    }
}

@Composable
private fun ReadingSection(state: AppSettingsUiState, viewModel: AppSettingsViewModel) {
    SettingsSection(
        eyebrow = stringResource(R.string.app_settings_defaults_eyebrow),
        title = stringResource(R.string.app_settings_reading),
    ) {
        SettingsChoiceRow(
            label = stringResource(R.string.detail_setting_direction),
            options = listOf(
                ReadingDirection.LeftToRight to stringResource(R.string.detail_option_ltr),
                ReadingDirection.RightToLeft to stringResource(R.string.detail_option_rtl),
            ),
            selected = state.direction,
            onSelect = viewModel::onDirectionSelected,
        )
        SettingsDivider()
        SettingsSwitchRow(
            label = stringResource(R.string.detail_setting_bubbles),
            checked = state.bubblesOnOpen,
            onCheckedChange = viewModel::onBubblesOnOpenChanged,
        )
        SettingsDivider()
        BubbleScaleRow(
            label = stringResource(R.string.app_settings_bubble_scale),
            scale = state.bubbleScale,
            onScaleCommitted = viewModel::onBubbleScaleChanged,
        )
        SettingsDivider()
        SettingsSwitchRow(
            label = stringResource(R.string.detail_setting_guided),
            checked = state.guidedOnOpen,
            onCheckedChange = viewModel::onGuidedOnOpenChanged,
        )
        SettingsDivider()
        SettingsSwitchRow(
            label = stringResource(R.string.app_settings_volume_keys),
            checked = state.volumeKeyPageTurn,
            onCheckedChange = viewModel::onVolumeKeyPageTurnChanged,
        )
    }
}

@Composable
private fun ScreenSection(state: AppSettingsUiState, viewModel: AppSettingsViewModel) {
    SettingsSection(
        eyebrow = stringResource(R.string.app_settings_reader_eyebrow),
        title = stringResource(R.string.app_settings_screen),
    ) {
        SettingsSwitchRow(
            label = stringResource(R.string.reader_action_night_tint),
            checked = state.nightTint,
            onCheckedChange = viewModel::onNightTintChanged,
        )
        SettingsDivider()
        SettingsSwitchRow(
            label = stringResource(R.string.app_settings_keep_screen_on),
            checked = state.keepScreenOn,
            onCheckedChange = viewModel::onKeepScreenOnChanged,
        )
    }
}

@Composable
private fun LibrarySection(folderUri: String?, scanning: Boolean, onPickFolder: () -> Unit, onRefresh: () -> Unit) {
    SettingsSection(
        eyebrow = stringResource(R.string.app_settings_collection_eyebrow),
        title = stringResource(R.string.library_title),
    ) {
        SettingsStackedRow(
            label = stringResource(R.string.app_settings_folder),
            supporting = folderUri?.let(::folderDisplayName) ?: stringResource(R.string.app_settings_no_folder),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ActionGap), verticalAlignment = Alignment.CenterVertically) {
                TonalAction(icon = Icons.Outlined.CreateNewFolder, label = stringResource(R.string.library_pick_folder), onClick = onPickFolder)
                if (folderUri != null) RescanAction(scanning = scanning, onRefresh = onRefresh)
            }
        }
    }
}

@Composable
private fun TonalAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = KapowTheme.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ActionCorner))
            .background(palette.selection)
            .clickable(onClick = onClick)
            .padding(horizontal = ActionPaddingHorizontal, vertical = ActionPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(ActionIconSize))
        Text(text = label, color = palette.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RescanAction(scanning: Boolean, onRefresh: () -> Unit) {
    val palette = KapowTheme.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ActionCorner))
            .clickable(enabled = !scanning, onClick = onRefresh)
            .padding(horizontal = ActionPaddingHorizontal, vertical = ActionPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(ActionIconSize), strokeWidth = 2.dp, color = palette.accent)
        } else {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(ActionIconSize))
        }
        Text(
            text = stringResource(if (scanning) R.string.library_scanning else R.string.library_refresh),
            color = palette.inkDim,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppearanceSection(theme: ThemeChoice, onThemeSelected: (ThemeChoice) -> Unit) {
    val context = LocalContext.current
    SettingsSection(
        eyebrow = stringResource(R.string.app_settings_theme_eyebrow),
        title = stringResource(R.string.app_settings_appearance),
    ) {
        SettingsStackedRow(label = stringResource(R.string.app_settings_ground)) {
            Row(horizontalArrangement = Arrangement.spacedBy(GroundTileGap)) {
                ThemeGround.entries.forEach { ground ->
                    GroundTile(
                        ground = ground,
                        accent = KapowPalette.of(ground, theme.accent.resolve(context)).accent,
                        label = stringResource(ground.labelRes()),
                        selected = ground == theme.ground,
                        onClick = { onThemeSelected(theme.copy(ground = ground)) },
                    )
                }
            }
        }
        SettingsDivider()
        SettingsStackedRow(label = stringResource(R.string.app_settings_accent)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeAccent.entries.forEach { accent ->
                    ThemeSwatch(
                        ground = theme.ground.background,
                        accent = KapowPalette.of(theme.ground, accent.resolve(context)).accent,
                        dynamic = accent == ThemeAccent.Dynamic,
                        selected = accent == theme.accent,
                        contentDescription = stringResource(accent.labelRes()),
                        onClick = { onThemeSelected(theme.copy(accent = accent)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroundTile(ground: ThemeGround, accent: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = KapowTheme.palette
    Column(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(GroundTileWidth)
                .height(GroundTileHeight)
                .clip(RoundedCornerShape(GroundTileCorner))
                .background(ground.background)
                .border(
                    width = if (selected) GroundTileRingWidth else SwatchOutlineWidth,
                    color = if (selected) palette.accent else palette.track,
                    shape = RoundedCornerShape(GroundTileCorner),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(GroundTileDot).clip(CircleShape).background(accent))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) palette.accent else palette.inkDim,
        )
    }
}

@Composable
private fun ThemeSwatch(ground: Color, accent: Color, dynamic: Boolean, selected: Boolean, contentDescription: String, onClick: () -> Unit) {
    val ring = KapowTheme.palette.inkDim
    val outline = KapowTheme.palette.track
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

private fun ThemeGround.labelRes(): Int = when (this) {
    ThemeGround.Black -> R.string.app_settings_ground_black
    ThemeGround.Graphite -> R.string.app_settings_ground_graphite
    ThemeGround.Paper -> R.string.app_settings_ground_paper
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

private const val SOURCE_REPOSITORY_URL = "https://github.com/sanchezpaco/kapow"

@Composable
private fun AboutSection(onReplayOnboarding: () -> Unit, onOpenLicences: () -> Unit) {
    val context = LocalContext.current
    SettingsSection(eyebrow = stringResource(R.string.app_name), title = stringResource(R.string.app_settings_about)) {
        SettingsNote(text = stringResource(R.string.app_settings_version, BuildConfig.VERSION_NAME, BuildConfig.BUILD_LABEL))
        SettingsDivider()
        SettingsActionRow(label = stringResource(R.string.app_settings_replay_onboarding), onClick = onReplayOnboarding)
        SettingsDivider()
        SettingsActionRow(label = stringResource(R.string.app_settings_licences), onClick = onOpenLicences)
        SettingsDivider()
        SettingsActionRow(label = stringResource(R.string.app_settings_source_code)) {
            context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_REPOSITORY_URL.toUri()))
        }
    }
}
