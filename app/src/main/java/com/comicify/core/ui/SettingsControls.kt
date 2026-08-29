package com.comicify.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.core.ui.theme.KapowTheme
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.domain.BUBBLE_SCALE_RANGE

private val BubbleScaleLabelWidth = 44.dp
private val BubbleScaleRowMaxWidth = 360.dp
private const val BUBBLE_SCALE_STEPS = 8

@Composable
fun defaultLabel(value: String): String = stringResource(R.string.detail_option_default_value, value)

fun ReadingDirection.labelRes(): Int = when (this) {
    ReadingDirection.LeftToRight -> R.string.detail_option_ltr
    ReadingDirection.RightToLeft -> R.string.detail_option_rtl
}

@Composable
fun SettingRow(label: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        options()
    }
}

@Composable
fun TriStateChips(selected: Boolean?, default: Boolean, onSelect: (Boolean?) -> Unit) {
    OptionChips(
        options = listOf(
            null to defaultLabel(stringResource(default.labelRes())),
            true to stringResource(R.string.detail_option_on),
            false to stringResource(R.string.detail_option_off),
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
fun OnOffChips(selected: Boolean, onSelect: (Boolean) -> Unit) {
    OptionChips(
        options = listOf(true to stringResource(R.string.detail_option_on), false to stringResource(R.string.detail_option_off)),
        selected = selected,
        onSelect = onSelect,
    )
}

fun Boolean.labelRes(): Int = if (this) R.string.detail_option_on else R.string.detail_option_off

@Composable
fun <T> OptionChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val palette = KapowTheme.palette
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) palette.accent else palette.inkDim,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) palette.selection else palette.raised)
                    .selectable(selected = active, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun BubbleScaleRow(scale: Float, onScaleCommitted: (Float) -> Unit) {
    var dragged by remember(scale) { mutableStateOf(scale) }
    Row(
        modifier = Modifier.widthIn(max = BubbleScaleRowMaxWidth).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = dragged.scaleLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(BubbleScaleLabelWidth),
        )
        ScaleBound(BUBBLE_SCALE_RANGE.start)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onScaleCommitted(dragged) },
            valueRange = BUBBLE_SCALE_RANGE,
            steps = BUBBLE_SCALE_STEPS,
            modifier = Modifier.weight(1f),
        )
        ScaleBound(BUBBLE_SCALE_RANGE.endInclusive)
    }
}

@Composable
private fun ScaleBound(value: Float) {
    Text(text = value.scaleLabel(), style = MaterialTheme.typography.labelSmall, color = KapowTheme.palette.inkFaint)
}

private fun Float.scaleLabel(): String = "%.1f×".format(this)

