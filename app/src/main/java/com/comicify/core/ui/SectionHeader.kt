package com.comicify.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comicify.core.ui.theme.KapowTheme

private val EyebrowLetterSpacing = 2.sp
private val HairlineThickness = 1.dp
private val SwitcherCornerRadius = 10.dp
private val SwitcherPadding = 8.dp
private const val TitleWeight = 1f

@Composable
fun SectionHeader(
    eyebrow: String,
    title: String,
    onTitleClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val palette = KapowTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = EyebrowLetterSpacing,
            color = palette.accent,
        )
        SectionTitle(title = title, onClick = onTitleClick)
        Box(modifier = Modifier.weight(1f).height(HairlineThickness).background(palette.hairline))
        trailing()
    }
}

@Composable
private fun RowScope.SectionTitle(title: String, onClick: (() -> Unit)?) {
    val text = @Composable {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (onClick == null) {
        text()
        return
    }
    Row(
        modifier = Modifier
            .weight(TitleWeight, fill = false)
            .clip(RoundedCornerShape(SwitcherCornerRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = SwitcherPadding, vertical = SwitcherPadding / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text()
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
