package com.comicify.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val OnDark = Color(0xFFF2F2F5)

object ComicifyTheme {
    val palette: ComicifyPalette
        @Composable @ReadOnlyComposable get() = LocalComicifyPalette.current
}

fun ThemeAccent.resolve(context: Context): Color =
    preset ?: dynamicAccent(context) ?: ThemeAccent.Red.preset!!

private fun dynamicAccent(context: Context): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return dynamicDarkColorScheme(context).primary
}

@Composable
fun ComicifyTheme(choice: ThemeChoice = ThemeChoice.Default, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palette = remember(choice) { ComicifyPalette.of(choice.ground, choice.accent.resolve(context)) }
    CompositionLocalProvider(LocalComicifyPalette provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(), typography = Typography(), content = content)
    }
}

private fun ComicifyPalette.toColorScheme() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    secondary = secondary,
    background = ground,
    onBackground = OnDark,
    surface = surface,
    onSurface = OnDark,
    surfaceVariant = raised,
    error = danger,
)
