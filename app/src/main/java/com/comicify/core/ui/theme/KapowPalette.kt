package com.comicify.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private const val SURFACE_TINT = 0.05f
private const val INK_TINT = 0.08f
private const val ACCENT_DEEP_MIX = 0.3f
private const val ACCENT_PALE_MIX = 0.55f
private const val ACCENT_ON_PAPER_MIX = 0.45f
private const val SELECTION_ALPHA = 0.16f

private data class Inks(val onGround: Color, val dim: Color, val faint: Color, val hairline: Color, val track: Color)

private val DarkInks = Inks(
    onGround = Color(0xFFF2F2F5),
    dim = Color(0xFFB4B1B4),
    faint = Color(0xFF807A7C),
    hairline = Color(0x12FFFFFF),
    track = Color(0x24FFFFFF),
)

private val PaperInks = Inks(
    onGround = Color(0xFF2A2622),
    dim = Color(0xFF5A544D),
    faint = Color(0xFF7A736A),
    hairline = Color(0x14000000),
    track = Color(0x28000000),
)

@Immutable
data class KapowPalette(
    val light: Boolean,
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val onGround: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentPale: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val hairline: Color,
    val track: Color,
) {
    val secondary: Color = Color(0xFFFFB300)
    val good: Color = Color(0xFF3FB27F)
    val danger: Color = Color(0xFFE62429)
    val onAccent: Color = Color.White
    val selection: Color = accent.copy(alpha = SELECTION_ALPHA)

    companion object {
        fun of(ground: ThemeGround, preset: Color): KapowPalette {
            val inks = if (ground.light) PaperInks else DarkInks
            val accent = if (ground.light) lerp(preset, Color.Black, ACCENT_ON_PAPER_MIX) else preset
            return KapowPalette(
                light = ground.light,
                ground = ground.background,
                surface = ground.surface,
                raised = lerp(ground.raised, accent, SURFACE_TINT),
                onGround = inks.onGround,
                accent = accent,
                accentDeep = lerp(accent, Color.Black, ACCENT_DEEP_MIX),
                accentPale = if (ground.light) accent else lerp(accent, Color.White, ACCENT_PALE_MIX),
                inkDim = lerp(inks.dim, accent, INK_TINT),
                inkFaint = lerp(inks.faint, accent, INK_TINT),
                hairline = inks.hairline,
                track = inks.track,
            )
        }
    }
}

val LocalKapowPalette = staticCompositionLocalOf { KapowPalette.of(ThemeGround.Black, ThemeAccent.Red.preset!!) }
