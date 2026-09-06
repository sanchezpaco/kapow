package com.comicify.feature.reader.domain

import androidx.compose.ui.graphics.ColorMatrix

private const val MID_LEVEL = 128f
private const val BRIGHTER_LIFT = 20f
private const val BRIGHTER_CONTRAST = 1.05f
private const val MORE_CONTRAST = 1.25f
private const val MORE_CONTRAST_SATURATION = 1.05f
private const val PAPER_CONTRAST = 1.18f

private data class ChannelGain(val red: Float, val green: Float, val blue: Float)

private val NeutralGain = ChannelGain(red = 1f, green = 1f, blue = 1f)
private val PaperGain = ChannelGain(red = 0.98f, green = 1f, blue = 1.06f)

enum class PageLook {
    Original,
    Brighter,
    MoreContrast,
    Paper;

    fun next(): PageLook = entries[(ordinal + 1) % entries.size]

    fun filter(): ColorMatrix? = when (this) {
        Original -> null
        Brighter -> levels(contrast = BRIGHTER_CONTRAST, lift = BRIGHTER_LIFT)
        MoreContrast -> levels(contrast = MORE_CONTRAST).saturatedBy(MORE_CONTRAST_SATURATION)
        Paper -> levels(contrast = PAPER_CONTRAST, gain = PaperGain)
    }

    companion object {
        fun named(name: String?): PageLook = entries.firstOrNull { it.name == name } ?: Original
    }
}

private fun levels(contrast: Float, lift: Float = 0f, gain: ChannelGain = NeutralGain): ColorMatrix {
    val offset = contrast * lift + MID_LEVEL * (1f - contrast)
    return ColorMatrix(
        floatArrayOf(
            gain.red * contrast, 0f, 0f, 0f, gain.red * offset,
            0f, gain.green * contrast, 0f, 0f, gain.green * offset,
            0f, 0f, gain.blue * contrast, 0f, gain.blue * offset,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

private fun ColorMatrix.saturatedBy(saturation: Float): ColorMatrix =
    ColorMatrix().apply { setToSaturation(saturation) }.also { it *= this }
