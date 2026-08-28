package com.comicify.feature.onboarding.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.core.ui.theme.ComicifyTheme
import com.comicify.feature.library.ui.PrimaryAction
import kotlinx.coroutines.launch

private const val STEP_COUNT = 3
private const val STEP_FOLDER = 0
private const val STEP_GESTURES = 1
private const val STEP_MODES = 2

private val ContentMaxWidth = 560.dp
private val IllustrationMaxWidth = 168.dp
private val IllustrationCorner = 18.dp
private val PageAspectRatio = 1.5f
private const val PAGE_TURN_ZONE_FRACTION = 0.28f
private const val ZONE_TINT_ALPHA = 0.22f
private val DotSize = 8.dp
private val DotGap = 8.dp

@Composable
fun OnboardingScreen(onFolderPicked: (Uri) -> Unit, onFinished: () -> Unit) {
    val pagerState = rememberPagerState { STEP_COUNT }
    val scope = rememberCoroutineScope()
    val palette = ComicifyTheme.palette
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onFolderPicked) }
    val lastStep = pagerState.currentPage == STEP_COUNT - 1
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.ground)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction(label = stringResource(R.string.onboarding_skip), onClick = onFinished)
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { step ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (step) {
                        STEP_FOLDER -> FolderStep(onPickFolder = { folderLauncher.launch(null) })
                        STEP_GESTURES -> GesturesStep()
                        STEP_MODES -> ModesStep()
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepDots(current = pagerState.currentPage)
            Spacer(modifier = Modifier.weight(1f))
            if (lastStep) {
                PrimaryAction(icon = Icons.Filled.ViewCarousel, label = stringResource(R.string.onboarding_start), onClick = onFinished)
            } else {
                TextAction(label = stringResource(R.string.onboarding_next)) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            }
        }
    }
}

@Composable
private fun FolderStep(onPickFolder: () -> Unit) {
    Illustration(ShelfDrawing())
    StepText(title = stringResource(R.string.onboarding_folder_title), body = stringResource(R.string.onboarding_folder_body))
    PrimaryAction(icon = Icons.Outlined.CreateNewFolder, label = stringResource(R.string.library_pick_folder), onClick = onPickFolder)
    Text(
        text = stringResource(R.string.onboarding_folder_sample),
        style = MaterialTheme.typography.bodySmall,
        color = ComicifyTheme.palette.inkFaint,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun GesturesStep() {
    Illustration(TapZonesDrawing())
    StepText(title = stringResource(R.string.onboarding_gestures_title), body = stringResource(R.string.onboarding_gestures_body))
}

@Composable
private fun ModesStep() {
    StepText(title = stringResource(R.string.onboarding_modes_title), body = stringResource(R.string.onboarding_modes_body))
    ModeRow(icon = Icons.Filled.ViewCarousel, title = stringResource(R.string.reader_action_guided), body = stringResource(R.string.onboarding_guided_body))
    ModeRow(icon = Icons.Filled.ChatBubbleOutline, title = stringResource(R.string.reader_action_enlarge_bubbles), body = stringResource(R.string.onboarding_bubbles_body))
}

@Composable
private fun StepText(title: String, body: String) {
    val palette = ComicifyTheme.palette
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = palette.onGround,
        textAlign = TextAlign.Center,
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = palette.inkDim,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ModeRow(icon: ImageVector, title: String, body: String) {
    val palette = ComicifyTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IllustrationCorner))
            .background(palette.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.onGround)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = palette.inkDim)
        }
    }
}

@Composable
private fun Illustration(content: DrawScope.() -> Unit) {
    val palette = ComicifyTheme.palette
    Canvas(
        modifier = Modifier
            .widthIn(max = IllustrationMaxWidth)
            .fillMaxWidth()
            .aspectRatio(1 / PageAspectRatio)
            .clip(RoundedCornerShape(IllustrationCorner))
            .background(palette.surface),
        onDraw = content,
    )
}

@Composable
private fun ShelfDrawing(): DrawScope.() -> Unit {
    val palette = ComicifyTheme.palette
    val spines = listOf(palette.accent, palette.accentPale, palette.secondary, palette.accentDeep, palette.good)
    return {
        val margin = size.width * 0.12f
        val gap = size.width * 0.03f
        val spineWidth = (size.width - 2 * margin - gap * (spines.size - 1)) / spines.size
        val shelfY = size.height * 0.78f
        spines.forEachIndexed { index, color ->
            val height = size.height * (0.42f + 0.06f * (index % 3))
            drawRoundRect(
                color = color,
                topLeft = Offset(margin + index * (spineWidth + gap), shelfY - height),
                size = Size(spineWidth, height),
                cornerRadius = CornerRadius(spineWidth * 0.15f),
            )
        }
        drawRect(color = palette.track, topLeft = Offset(margin * 0.6f, shelfY), size = Size(size.width - margin * 1.2f, size.height * 0.03f))
    }
}

@Composable
private fun TapZonesDrawing(): DrawScope.() -> Unit {
    val palette = ComicifyTheme.palette
    return {
        val zoneWidth = size.width * PAGE_TURN_ZONE_FRACTION
        val tint = palette.accent.copy(alpha = ZONE_TINT_ALPHA)
        drawRect(color = tint, size = Size(zoneWidth, size.height))
        drawRect(color = tint, topLeft = Offset(size.width - zoneWidth, 0f), size = Size(zoneWidth, size.height))
        drawPanels(palette.track)
        drawChevron(Offset(zoneWidth / 2f, size.height / 2f), pointingRight = false, palette.accent)
        drawChevron(Offset(size.width - zoneWidth / 2f, size.height / 2f), pointingRight = true, palette.accent)
        drawCircle(color = palette.accent, radius = size.width * 0.05f, center = center)
        drawCircle(color = palette.accent.copy(alpha = ZONE_TINT_ALPHA), radius = size.width * 0.1f, center = center)
    }
}

private fun DrawScope.drawPanels(color: Color) {
    val inset = size.width * 0.08f
    val gutter = size.width * 0.03f
    val stroke = Stroke(width = size.width * 0.008f)
    val innerWidth = size.width - 2 * inset
    val innerHeight = size.height - 2 * inset
    val rowHeight = (innerHeight - gutter) / 2f
    val cellWidth = (innerWidth - gutter) / 2f
    drawRoundRect(color, Offset(inset, inset), Size(innerWidth, rowHeight), CornerRadius(gutter), stroke)
    drawRoundRect(color, Offset(inset, inset + rowHeight + gutter), Size(cellWidth, rowHeight), CornerRadius(gutter), stroke)
    drawRoundRect(color, Offset(inset + cellWidth + gutter, inset + rowHeight + gutter), Size(cellWidth, rowHeight), CornerRadius(gutter), stroke)
}

private fun DrawScope.drawChevron(at: Offset, pointingRight: Boolean, color: Color) {
    val arm = size.width * 0.05f
    val direction = if (pointingRight) 1f else -1f
    val tip = Offset(at.x + arm * direction / 2f, at.y)
    val stroke = size.width * 0.012f
    drawLine(color, Offset(tip.x - arm * direction, at.y - arm), tip, stroke)
    drawLine(color, Offset(tip.x - arm * direction, at.y + arm), tip, stroke)
}

@Composable
private fun StepDots(current: Int) {
    val palette = ComicifyTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(DotGap), verticalAlignment = Alignment.CenterVertically) {
        repeat(STEP_COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(width = if (index == current) DotSize * 2.5f else DotSize, height = DotSize)
                    .clip(CircleShape)
                    .background(if (index == current) palette.accent else palette.track),
            )
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = ComicifyTheme.palette.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
