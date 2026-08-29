package com.comicify.core.ui.splash

import android.content.Context
import android.provider.Settings

object ColdStartSplash {
    private var pending = true

    fun claim(context: Context): Boolean {
        if (!pending) return false
        pending = false
        return animationsEnabled(context)
    }

    private fun animationsEnabled(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}
