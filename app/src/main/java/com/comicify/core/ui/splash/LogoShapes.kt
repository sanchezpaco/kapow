package com.comicify.core.ui.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

internal object LogoShapes {
    const val VIEWPORT = 108f
    val centre = Offset(54f, 54f)
    const val PAGE_TILT_DEGREES = -8f
    const val ICON_CIRCLE_DIAMETER_DP = 192f
    const val ICON_VISIBLE_UNITS = 72f

    val ink = Color(0xFF0B0B0F)
    val white = Color.White
    val yellow = Color(0xFFFFC107)
    val red = Color(0xFFFF3D45)
    val panelDark = Color(0xFF0E2445)
    val panelMid = Color(0xFF17396A)
    val gutter = Color(0xFF4C86C4)

    val panels: List<Rect> = listOf(
        Rect(-50f, -50f, 10f, 60f), Rect(14f, -50f, 80f, 20f), Rect(84f, -50f, 160f, 40f),
        Rect(14f, 24f, 46f, 60f), Rect(50f, 24f, 80f, 60f), Rect(84f, 44f, 160f, 100f),
        Rect(-50f, 64f, 30f, 160f), Rect(34f, 64f, 80f, 110f), Rect(84f, 104f, 160f, 160f),
        Rect(34f, 114f, 80f, 160f),
    )
    const val PANEL_STROKE = 1.6f
    fun panelFill(index: Int): Color = if (index % 3 == 0) panelDark else panelMid
    fun panelPerimeter(rect: Rect): Float = 2f * (rect.width + rect.height)

    val vignetteCentre = Offset(54f, 48.6f)
    const val VIGNETTE_RADIUS = 81f
    const val VIGNETTE_CLEAR_STOP = 0.35f
    const val VIGNETTE_EDGE_ALPHA = 0.55f

    val bubble: Path = pathOf("M20,50 a34,25 0 1 1 68,0 a34,25 0 0 1 -19,22 L70,88 L56,75 a34,25 0 0 1 -36,-25 z")
    const val BUBBLE_HALO = 7f
    const val BUBBLE_STROKE = 3f

    val k: Path = pathOf(
        "M66.47 31.41 57.37 47.22 67.98 61.91 57.93 64.79 50.21 55.97 50.91 65.98 41.77 67.30 " +
            "38.92 33.41 49.26 32.90 49.52 42.96 55.25 31.69Z",
    )
    const val K_STROKE = 2.2f
    const val K_SHADOW_STROKE = 3.6f
    val kShadowOffset = Offset(1.1f, 1.3f)
    const val K_GRADIENT_TOP = 32f
    const val K_GRADIENT_BOTTOM = 66f

    const val LAUNCHER_MARK_SCALE = 0.64f

    private fun pathOf(data: String): Path = PathParser().parsePathString(data).toPath()
}
