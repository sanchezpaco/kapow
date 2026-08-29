package com.comicify.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.comicify.core.ui.theme.KapowTheme

internal val Accent: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.accent
internal val AccentDeep: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.accentDeep
internal val AccentPale: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.accentPale
internal val AccentAmber: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.secondary
internal val Good: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.good
internal val Danger: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.danger
internal val InkDim: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.inkDim
internal val InkFaint: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.inkFaint
internal val Surface2: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.raised
internal val CardLine: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.hairline
internal val CoverTrack: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.track
internal val Ground: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.ground
internal val OnGround: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.onGround
internal val HeroGround: Color @Composable @ReadOnlyComposable get() = KapowTheme.palette.surface
internal const val HeroGlowMix = 0.55f
internal const val HeroTintMix = 0.35f
