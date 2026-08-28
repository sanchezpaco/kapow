package com.comicify.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.comicify.core.ui.theme.ComicifyTheme

internal val Accent: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.accent
internal val AccentDeep: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.accentDeep
internal val AccentPale: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.accentPale
internal val AccentAmber: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.secondary
internal val Good: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.good
internal val Danger: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.danger
internal val InkDim: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.inkDim
internal val InkFaint: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.inkFaint
internal val Surface2: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.raised
internal val CardLine: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.hairline
internal val CoverTrack: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.track
internal val HeroGround: Color @Composable @ReadOnlyComposable get() = ComicifyTheme.palette.surface
internal const val HeroGlowMix = 0.55f
internal const val HeroTintMix = 0.35f
