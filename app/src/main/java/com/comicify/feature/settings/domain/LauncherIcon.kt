package com.comicify.feature.settings.domain

import androidx.annotation.DrawableRes
import com.comicify.R

enum class LauncherIcon(val alias: String, @DrawableRes val mipmap: Int) {
    Blue("LauncherBlue", R.mipmap.ic_launcher),
    Ink("LauncherInk", R.mipmap.ic_launcher_ink),
    Red("LauncherRed", R.mipmap.ic_launcher_red),
    Violet("LauncherViolet", R.mipmap.ic_launcher_violet),
    Mix("LauncherMix", R.mipmap.ic_launcher_mix);

    companion object {
        val Default = Blue
    }
}
