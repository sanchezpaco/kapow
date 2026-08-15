package com.comicify.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ComicRed = Color(0xFFE62429)
private val ComicAmber = Color(0xFFFFC107)
private val PureBlack = Color(0xFF000000)
private val NearBlack = Color(0xFF0B0B0F)
private val Surface = Color(0xFF16161C)
private val OnDark = Color(0xFFF2F2F5)

private val ComicifyDarkColors = darkColorScheme(
    primary = ComicRed,
    onPrimary = OnDark,
    secondary = ComicAmber,
    background = PureBlack,
    onBackground = OnDark,
    surface = NearBlack,
    onSurface = OnDark,
    surfaceVariant = Surface,
)

private val ComicifyLightColors = lightColorScheme(
    primary = ComicRed,
    secondary = ComicAmber,
)

@Composable
fun ComicifyTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) ComicifyDarkColors else ComicifyLightColors,
        typography = Typography(),
        content = content,
    )
}
