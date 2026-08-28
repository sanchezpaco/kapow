package com.comicify.feature.library.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedCover(comicId: Long): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return with(transition) {
        sharedElement(rememberSharedContentState(key = "cover-$comicId"), animatedVisibilityScope = visibility)
    }
}
