package com.comicify.feature.review.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview

private const val REVIEW_TAG = "InAppReview"

@Composable
fun InAppReviewPrompt(viewModel: ReviewPromptViewModel) {
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(viewModel) {
        val manager = ReviewManagerFactory.create(activity)
        viewModel.promptRequests.collect { manager.launchReviewFlowOrLog(activity) }
    }
}

private suspend fun ReviewManager.launchReviewFlowOrLog(activity: Activity) {
    try {
        launchReview(activity, requestReview())
    } catch (unavailable: ReviewException) {
        Log.w(REVIEW_TAG, "In-app review unavailable (code ${unavailable.errorCode})")
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity in context chain")
}
