package com.comicify.feature.settings.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.comicify.MainActivity
import com.comicify.feature.settings.domain.LauncherIcon
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val ALIAS_PACKAGE = MainActivity::class.java.name.substringBeforeLast('.')

@Singleton
class LauncherIconRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun current(): LauncherIcon =
        LauncherIcon.entries.firstOrNull(::isEnabled) ?: LauncherIcon.Default

    fun select(icon: LauncherIcon) {
        if (current() == icon) return
        setState(icon, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        LauncherIcon.entries
            .filter { it != icon }
            .forEach { setState(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
    }

    private fun isEnabled(icon: LauncherIcon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(componentOf(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.Default
            else -> false
        }

    private fun setState(icon: LauncherIcon, state: Int) {
        context.packageManager.setComponentEnabledSetting(
            componentOf(icon),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun componentOf(icon: LauncherIcon) =
        ComponentName(context.packageName, "$ALIAS_PACKAGE.${icon.alias}")
}
