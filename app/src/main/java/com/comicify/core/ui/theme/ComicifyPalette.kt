package com.comicify.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private const val SURFACE_TINT = 0.05f
private const val INK_TINT = 0.08f
private const val ACCENT_DEEP_MIX = 0.3f
private const val ACCENT_PALE_MIX = 0.55f
private const val SELECTION_ALPHA = 0.16f
private val NeutralInkDim = Color(0xFFB4B1B4)
private val NeutralInkFaint = Color(0xFF807A7C)

@Immutable
data class ComicifyPalette(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentPale: Color,
    val inkDim: Color,
    val inkFaint: Color,
) {
    val secondary: Color = Color(0xFFFFB300)
    val good: Color = Color(0xFF3FB27F)
    val danger: Color = Color(0xFFE62429)
    val onAccent: Color = Color.White
    val hairline: Color = Color(0x12FFFFFF)
    val track: Color = Color(0x24FFFFFF)
    val selection: Color = accent.copy(alpha = SELECTION_ALPHA)

    companion object {
        fun of(ground: ThemeGround, accent: Color): ComicifyPalette = ComicifyPalette(
            ground = ground.background,
            surface = ground.surface,
            raised = lerp(ground.raised, accent, SURFACE_TINT),
            accent = accent,
            accentDeep = lerp(accent, Color.Black, ACCENT_DEEP_MIX),
            accentPale = lerp(accent, Color.White, ACCENT_PALE_MIX),
            inkDim = lerp(NeutralInkDim, accent, INK_TINT),
            inkFaint = lerp(NeutralInkFaint, accent, INK_TINT),
        )
    }
}

val LocalComicifyPalette = staticCompositionLocalOf { ComicifyPalette.of(ThemeGround.Black, ThemeAccent.Red.preset!!) }
