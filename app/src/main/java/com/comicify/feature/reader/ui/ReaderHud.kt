package com.comicify.feature.reader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun readingProgress(current: Int, total: Int): Float =
    if (total <= 0) 0f else ((current + 1).toFloat() / total).coerceIn(0f, 1f)

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.16f),
) {
    val animated by animateFloatAsState(targetValue = progress, animationSpec = tween(500), label = "progress")
    val fill = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind {
                drawRoundRect(color = trackColor, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(
                    brush = fill,
                    size = Size(size.width * animated, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                    topLeft = Offset.Zero,
                )
            },
    )
}

@Composable
fun PageCounter(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "%02d / %02d".format(current + 1, total),
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
