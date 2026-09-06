package com.comicify.feature.stats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.R
import com.comicify.core.ui.SectionHeader
import com.comicify.core.ui.theme.KapowTheme
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.ui.CoverArt
import com.comicify.feature.library.ui.GhostAction
import com.comicify.feature.library.ui.PrimaryAction
import com.comicify.feature.stats.domain.DayPages
import com.comicify.feature.stats.domain.ReadingStats
import com.comicify.feature.stats.domain.STATS_WINDOW_DAYS
import com.comicify.feature.stats.domain.SeriesPace

private val ScreenPadding = 20.dp
private val SectionGap = 22.dp
private val BlockGap = 12.dp
private val ColumnGap = 20.dp
private val TwoColumnMinWidth = 840.dp
private val ContentMaxWidth = 1040.dp
private val TileGap = 10.dp
private val TileUnitGap = 3.dp
private val TileCorner = 16.dp
private val TilePadding = 14.dp
private val ChartHeightFolded = 96.dp
private val ChartHeightUnfolded = 110.dp
private val BarGap = 2.dp
private val BarCorner = 3.dp
private val BarStubHeight = 2.dp
private val BarLabelHeight = 15.dp
private val HairlineThickness = 1.dp
private val MeterHeight = 6.dp
private val MeterCorner = 3.dp
private val MosaicGap = 8.dp
private val MosaicCorner = 8.dp
private val MosaicBadgeSize = 18.dp
private val MosaicBadgeIcon = 11.dp
private val EmptyBlockSize = 88.dp
private val EmptyBlockCorner = 20.dp
private val EmptyIconSize = 38.dp
private val EmptyTopGap = 48.dp
private const val MOSAIC_CAP = 12
private const val MOSAIC_COLUMNS_FOLDED = 4
private const val MOSAIC_COLUMNS_UNFOLDED = 6
private const val COVER_RATIO = 2f / 3f
private const val TABULAR_FIGURES = "tnum"

@Composable
fun StatsScreen(onBack: () -> Unit, onOpenComic: (LibraryComic) -> Unit) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        val twoColumns = maxWidth >= TwoColumnMinWidth
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ContentMaxWidth + ScreenPadding * 2)
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            StatsHeader(onBack = onBack)
            val stats = state.stats
            when {
                state.loading -> Unit
                stats == null || !stats.enoughToShow -> EmptyStats(resume = state.resume, onOpenComic = onOpenComic)
                twoColumns -> UnfoldedStats(stats = stats, finished = state.finished, onOpenComic = onOpenComic)
                else -> FoldedStats(stats = stats, finished = state.finished, onOpenComic = onOpenComic)
            }
        }
    }
}

@Composable
private fun tabularFigures(): TextStyle = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES)

@Composable
private fun StatsHeader(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        GhostAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.library_back),
            onClick = onBack,
        )
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.6).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun FoldedStats(stats: ReadingStats, finished: List<LibraryComic>, onOpenComic: (LibraryComic) -> Unit) {
    HeroFigure(pages = stats.pages)
    TileGrid(stats = stats, finished = finished.size)
    PagesPerDayBlock(perDay = stats.perDay, average = stats.averagePagesPerDay, height = ChartHeightFolded)
    SeriesPaceBlock(series = stats.series)
    FinishedBlock(comics = finished, columns = MOSAIC_COLUMNS_FOLDED, onOpenComic = onOpenComic)
    PrivacyNote()
}

@Composable
private fun UnfoldedStats(stats: ReadingStats, finished: List<LibraryComic>, onOpenComic: (LibraryComic) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
            HeroFigure(pages = stats.pages)
            TileGrid(stats = stats, finished = finished.size)
            PagesPerDayBlock(perDay = stats.perDay, average = stats.averagePagesPerDay, height = ChartHeightUnfolded)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
            SeriesPaceBlock(series = stats.series)
            FinishedBlock(comics = finished, columns = MOSAIC_COLUMNS_UNFOLDED, onOpenComic = onOpenComic)
        }
    }
    PrivacyNote()
}

@Composable
private fun HeroFigure(pages: Int) {
    val palette = KapowTheme.palette
    Column {
        Text(
            text = stringResource(R.string.stats_window_30_days).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            color = palette.accent,
        )
        Text(
            text = figure(pages),
            fontSize = 72.sp,
            lineHeight = 76.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-3).sp,
            style = tabularFigures(),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.stats_hero_pages),
            fontSize = 14.sp,
            color = palette.inkDim,
        )
    }
}

@Composable
private fun TileGrid(stats: ReadingStats, finished: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            Tile(label = stringResource(R.string.stats_tile_time), modifier = Modifier.weight(1f)) {
                TimeValue(millis = stats.millis)
            }
            Tile(label = stringResource(R.string.stats_tile_pace), modifier = Modifier.weight(1f)) {
                TileFigure(figure(stats.secondsPerPage))
                TileUnit(stringResource(R.string.stats_unit_pace))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            Tile(
                label = stringResource(R.string.stats_tile_finished),
                modifier = Modifier.weight(1f),
                sub = stringResource(R.string.stats_tile_finished_sub),
            ) {
                TileFigure(figure(finished))
            }
            Tile(label = stringResource(R.string.stats_tile_days), modifier = Modifier.weight(1f)) {
                TileFigure(stringResource(R.string.stats_value_days, figure(stats.daysRead), figure(STATS_WINDOW_DAYS)))
            }
        }
    }
}

@Composable
private fun RowScope.TimeValue(millis: Long) {
    val (hours, minutes) = hoursAndMinutes(millis)
    if (hours > 0) {
        TileFigure(figure(hours))
        TileUnit(stringResource(R.string.stats_unit_hours))
    }
    TileFigure(figure(minutes))
    TileUnit(stringResource(R.string.stats_unit_minutes))
}

@Composable
private fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    value: @Composable RowScope.() -> Unit,
) {
    val palette = KapowTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(TileCorner))
            .background(palette.raised)
            .padding(TilePadding),
    ) {
        Text(text = label, fontSize = 11.sp, color = palette.inkFaint)
        Row(horizontalArrangement = Arrangement.spacedBy(TileUnitGap), content = value)
        if (sub != null) Text(text = sub, fontSize = 13.sp, color = palette.inkDim)
    }
}

@Composable
private fun RowScope.TileFigure(text: String) {
    Text(
        text = text,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp,
        style = tabularFigures(),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        modifier = Modifier.alignByBaseline(),
    )
}

@Composable
private fun RowScope.TileUnit(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = KapowTheme.palette.inkDim,
        maxLines = 1,
        modifier = Modifier.alignByBaseline(),
    )
}

@Composable
private fun PagesPerDayBlock(perDay: List<DayPages>, average: Int, height: Dp) {
    Block(eyebrow = stringResource(R.string.stats_pace_eyebrow), title = stringResource(R.string.stats_pace_section)) {
        PagesPerDayChart(perDay = perDay, height = height)
        ChartFooter(start = perDay.first(), average = average)
    }
}

@Composable
private fun PagesPerDayChart(perDay: List<DayPages>, height: Dp) {
    val palette = KapowTheme.palette
    val peak = perDay.maxOf { it.pages }
    val peakDay = perDay.indexOfFirst { it.pages == peak }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(height + BarLabelHeight),
            horizontalArrangement = Arrangement.spacedBy(BarGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            perDay.forEachIndexed { index, day ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.height(BarLabelHeight), contentAlignment = Alignment.Center) {
                        if (peak > 0 && index == peakDay) {
                            Text(
                                text = figure(peak),
                                fontSize = 10.sp,
                                style = tabularFigures(),
                                color = palette.inkDim,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight(day.pages, peak, height))
                            .clip(RoundedCornerShape(topStart = BarCorner, topEnd = BarCorner))
                            .background(if (day.pages > 0) palette.accent else palette.track),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(HairlineThickness).background(palette.hairline))
    }
}

private fun barHeight(pages: Int, peak: Int, height: Dp): Dp {
    if (pages <= 0 || peak <= 0) return BarStubHeight
    return maxOf(BarStubHeight, height * (pages.toFloat() / peak))
}

@Composable
private fun ChartFooter(start: DayPages, average: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ChartFooterLabel(dayLabel(start.date))
        ChartFooterLabel(stringResource(R.string.stats_chart_average, figure(average)))
        ChartFooterLabel(stringResource(R.string.stats_chart_today))
    }
}

@Composable
private fun ChartFooterLabel(text: String) {
    Text(text = text, fontSize = 10.sp, color = KapowTheme.palette.inkFaint)
}

@Composable
private fun SeriesPaceBlock(series: List<SeriesPace>) {
    if (series.isEmpty()) return
    Block(eyebrow = stringResource(R.string.stats_series_eyebrow), title = stringResource(R.string.stats_series_section)) {
        val longest = series.maxOf { it.millis }
        series.forEach { pace -> SeriesRow(pace = pace, longest = longest) }
    }
}

@Composable
private fun SeriesRow(pace: SeriesPace, longest: Long) {
    val palette = KapowTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = pace.series,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = duration(pace.millis),
                fontSize = 11.sp,
                style = tabularFigures(),
                color = palette.inkDim,
            )
        }
        Text(
            text = stringResource(
                R.string.stats_series_meta,
                pluralStringResource(R.plurals.stats_series_issues, pace.issues, figure(pace.issues)),
                stringResource(R.string.stats_value_pace, figure(pace.secondsPerPage)),
            ),
            fontSize = 11.sp,
            style = tabularFigures(),
            color = palette.inkFaint,
        )
        Meter(fraction = if (longest > 0) pace.millis.toFloat() / longest else 0f)
    }
}

@Composable
private fun Meter(fraction: Float) {
    val palette = KapowTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .clip(RoundedCornerShape(MeterCorner))
            .background(palette.track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(MeterCorner))
                .background(palette.accent),
        )
    }
}

@Composable
private fun FinishedBlock(comics: List<LibraryComic>, columns: Int, onOpenComic: (LibraryComic) -> Unit) {
    if (comics.isEmpty()) return
    Block(eyebrow = stringResource(R.string.stats_finished_eyebrow), title = stringResource(R.string.stats_finished_section)) {
        FinishedMosaic(comics = comics, columns = columns, onOpenComic = onOpenComic)
    }
}

@Composable
private fun FinishedMosaic(comics: List<LibraryComic>, columns: Int, onOpenComic: (LibraryComic) -> Unit) {
    val overflowing = comics.size > MOSAIC_CAP
    val shown = if (overflowing) comics.take(MOSAIC_CAP - 1) else comics
    val hidden = comics.size - shown.size
    val tiles = shown.size + if (overflowing) 1 else 0
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MosaicGap),
        verticalArrangement = Arrangement.spacedBy(MosaicGap),
        maxItemsInEachRow = columns,
    ) {
        shown.forEach { comic ->
            MosaicCover(comic = comic, modifier = Modifier.weight(1f), onClick = { onOpenComic(comic) })
        }
        if (overflowing) MosaicOverflow(hidden = hidden, modifier = Modifier.weight(1f))
        repeat((columns - tiles % columns) % columns) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun MosaicCover(comic: LibraryComic, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(COVER_RATIO)
            .clip(RoundedCornerShape(MosaicCorner))
            .clickable(onClick = onClick),
    ) {
        CoverArt(comic = comic, showArtwork = true)
        FinishedBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp))
    }
}

@Composable
private fun FinishedBadge(modifier: Modifier) {
    Box(
        modifier = modifier.size(MosaicBadgeSize).clip(CircleShape).background(KapowTheme.palette.good),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(R.string.library_completed),
            tint = Color.White,
            modifier = Modifier.size(MosaicBadgeIcon),
        )
    }
}

@Composable
private fun MosaicOverflow(hidden: Int, modifier: Modifier) {
    val palette = KapowTheme.palette
    Box(
        modifier = modifier
            .aspectRatio(COVER_RATIO)
            .clip(RoundedCornerShape(MosaicCorner))
            .background(palette.raised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.stats_finished_more, figure(hidden)),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = tabularFigures(),
            color = palette.inkDim,
        )
    }
}

@Composable
private fun Block(eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BlockGap)) {
        SectionHeader(eyebrow = eyebrow, title = title)
        content()
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        text = stringResource(R.string.stats_privacy_note),
        fontSize = 11.sp,
        color = KapowTheme.palette.inkFaint,
    )
}

@Composable
private fun EmptyStats(resume: LibraryComic?, onOpenComic: (LibraryComic) -> Unit) {
    val palette = KapowTheme.palette
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = EmptyTopGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        Box(
            modifier = Modifier
                .size(EmptyBlockSize)
                .clip(RoundedCornerShape(EmptyBlockCorner))
                .background(palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(EmptyIconSize),
            )
        }
        Text(
            text = stringResource(R.string.stats_empty_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.stats_empty_body),
            fontSize = 13.sp,
            color = palette.inkDim,
            textAlign = TextAlign.Center,
        )
        if (resume != null) {
            PrimaryAction(
                icon = Icons.Filled.PlayArrow,
                label = stringResource(R.string.stats_empty_resume, resume.title),
                onClick = { onOpenComic(resume) },
            )
        }
    }
}
