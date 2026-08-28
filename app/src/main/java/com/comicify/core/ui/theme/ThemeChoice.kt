package com.comicify.core.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeGround(val background: Color, val surface: Color, val raised: Color) {
    Black(background = Color(0xFF000000), surface = Color(0xFF0B0B0F), raised = Color(0xFF151518)),
    Graphite(background = Color(0xFF16161B), surface = Color(0xFF1C1C22), raised = Color(0xFF26262D)),
}

enum class ThemeAccent(val preset: Color?) {
    Red(Color(0xFFFF3D45)),
    Amber(Color(0xFFFFB300)),
    Orange(Color(0xFFFF8A2B)),
    Green(Color(0xFF4CD08F)),
    Cyan(Color(0xFF2EC4F1)),
    Violet(Color(0xFFB388FF)),
    Pink(Color(0xFFFF5CA8)),
    Dynamic(null),
}

data class ThemeChoice(val ground: ThemeGround, val accent: ThemeAccent) {
    companion object {
        val Default = ThemeChoice(ThemeGround.Black, ThemeAccent.Red)
    }
}
