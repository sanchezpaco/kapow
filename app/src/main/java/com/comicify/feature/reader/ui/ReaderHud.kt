package com.comicify.feature.reader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comicify.R

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
fun GuidedStops(current: Int, count: Int, modifier: Modifier = Modifier) {
    val active = MaterialTheme.colorScheme.primary
    val idle = Color.White.copy(alpha = 0.3f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (index in 0 until count) {
            val isActive = index == current
            val isOverview = index == 0
            val tint = if (isActive) active else idle
            Box(
                modifier = Modifier
                    .size(
                        width = if (isOverview) 12.dp else if (isActive) 8.dp else 6.dp,
                        height = if (isOverview || isActive) 8.dp else 6.dp,
                    )
                    .clip(if (isOverview) RoundedCornerShape(2.dp) else CircleShape)
                    .background(tint),
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.reader_action_next_page),
            tint = if (current >= count - 1) active else Color.White.copy(alpha = 0.22f),
            modifier = Modifier.size(16.dp),
        )
    }
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
