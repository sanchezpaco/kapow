package com.comicify.feature.settings.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.comicify.R
import com.comicify.core.ui.theme.ComicifyTheme
import com.comicify.feature.library.ui.GhostAction

private val LicencesContentMaxWidth = 640.dp

private data class Attribution(val name: String, val licence: String, val url: String)

private val attributions = listOf(
    Attribution("7-Zip-JBinding-4Android", "LGPL-2.1 (7-Zip: LGPL + unRAR restriction)", "https://github.com/omicronapps/7-Zip-JBinding-4Android"),
    Attribution("ONNX Runtime", "MIT", "https://github.com/microsoft/onnxruntime"),
    Attribution("manga-panel-detector-yolo26n (leoxs22)", "Apache-2.0", "https://huggingface.co/leoxs22/manga-panel-detector-yolo26n"),
    Attribution("comic-speech-bubble-detector-yolov8m (ogkalu)", "Apache-2.0", "https://huggingface.co/ogkalu/comic-speech-bubble-detector-yolov8m"),
    Attribution("Ultralytics YOLO26", "AGPL-3.0", "https://github.com/ultralytics/ultralytics"),
    Attribution("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    Attribution("Jetpack Compose, AndroidX, Material Icons", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    Attribution("Kotlin, kotlinx.coroutines", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    Attribution("Dagger Hilt", "Apache-2.0", "https://github.com/google/dagger"),
)

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val palette = ComicifyTheme.palette
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .widthIn(max = LicencesContentMaxWidth),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GhostAction(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), onClick = onBack)
        Text(
            text = stringResource(R.string.licences_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.licences_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkDim,
        )
        attributions.forEach { attribution ->
            AttributionRow(attribution) {
                context.startActivity(Intent(Intent.ACTION_VIEW, attribution.url.toUri()))
            }
        }
    }
}

@Composable
private fun AttributionRow(attribution: Attribution, onOpen: () -> Unit) {
    val palette = ComicifyTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = attribution.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.onGround)
        Text(text = attribution.licence, style = MaterialTheme.typography.bodySmall, color = palette.inkDim)
        Text(text = attribution.url, style = MaterialTheme.typography.bodySmall, color = palette.accent)
    }
}
