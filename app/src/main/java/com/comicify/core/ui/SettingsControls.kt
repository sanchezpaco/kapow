package com.comicify.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

private val SectionCorner = 20.dp
private val SectionHeaderGap = 10.dp
private val RowMinHeight = 56.dp
private val RowPadding = 16.dp
private val RowVerticalPadding = 10.dp
private val SliderRowVerticalPadding = 14.dp
private val LabelControlGap = 12.dp
private val SupportingGap = 2.dp
private val ChipGap = 8.dp
private val ChipBorderWidth = 1.dp
private val ChipPaddingHorizontal = 14.dp
private val ChipPaddingVertical = 8.dp
private val PillPaddingHorizontal = 10.dp
private val PillPaddingVertical = 4.dp
private val ChevronSize = 20.dp
private val ThumbSize = 20.dp
private val HairlineThickness = 1.dp
private const val BUBBLE_SCALE_STEPS = 8
private const val TABULAR_FIGURES = "tnum"

@Composable
fun defaultLabel(value: String): String = stringResource(R.string.detail_option_default_value, value)

fun ReadingDirection.labelRes(): Int = when (this) {
    ReadingDirection.LeftToRight -> R.string.detail_option_ltr
    ReadingDirection.RightToLeft -> R.string.detail_option_rtl
}

fun Boolean.labelRes(): Int = if (this) R.string.detail_option_on else R.string.detail_option_off

@Composable
fun SettingsSection(eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionHeaderGap)) {
        SectionHeader(eyebrow = eyebrow, title = title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SectionCorner))
                .background(KapowTheme.palette.raised),
            content = content,
        )
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = RowPadding),
        thickness = HairlineThickness,
        color = KapowTheme.palette.hairline,
    )
}

@Composable
fun SettingsRow(label: String, supporting: String? = null, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).rowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LabelControlGap),
    ) {
        RowLabel(label = label, supporting = supporting, modifier = Modifier.weight(1f))
        control()
    }
}

@Composable
fun SettingsStackedRow(label: String, supporting: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).rowPadding(),
        verticalArrangement = Arrangement.spacedBy(LabelControlGap),
    ) {
        RowLabel(label = label, supporting = supporting)
        content()
    }
}

@Composable
fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, supporting: String? = null) {
    val palette = KapowTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .rowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LabelControlGap),
    ) {
        RowLabel(label = label, supporting = supporting, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccent,
                checkedTrackColor = palette.accent,
                checkedBorderColor = palette.accent,
                uncheckedThumbColor = palette.inkDim,
                uncheckedTrackColor = palette.track,
                uncheckedBorderColor = palette.inkFaint,
            ),
        )
    }
}

@Composable
fun <T> SettingsChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, supporting: String? = null) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).rowPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(ChipGap),
        itemVerticalAlignment = Alignment.CenterVertically,
        maxItemsInEachRow = 2,
    ) {
        RowLabel(label = label, supporting = supporting)
        OptionChips(options = options, selected = selected, onSelect = onSelect)
    }
}

@Composable
fun SettingsActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(onClick = onClick)
            .rowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LabelControlGap),
    ) {
        RowLabel(label = label, supporting = null, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = KapowTheme.palette.inkFaint,
            modifier = Modifier.size(ChevronSize),
        )
    }
}

@Composable
fun SettingsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KapowTheme.palette.inkDim,
        modifier = Modifier.padding(horizontal = RowPadding, vertical = LabelControlGap),
    )
}

@Composable
fun triStateOptions(default: Boolean): List<Pair<Boolean?, String>> = listOf(
    null to defaultLabel(stringResource(default.labelRes())),
    true to stringResource(R.string.detail_option_on),
    false to stringResource(R.string.detail_option_off),
)

@Composable
private fun RowLabel(label: String, supporting: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SupportingGap)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (supporting != null) {
            Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = KapowTheme.palette.inkDim)
        }
    }
}

private fun Modifier.rowPadding(): Modifier = padding(horizontal = RowPadding, vertical = RowVerticalPadding)

@Composable
fun <T> OptionChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val palette = KapowTheme.palette
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipGap), verticalArrangement = Arrangement.spacedBy(ChipGap)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) palette.accent else palette.inkDim,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) palette.selection else palette.track)
                    .then(if (active) Modifier.border(ChipBorderWidth, palette.accent, CircleShape) else Modifier)
                    .selectable(selected = active, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleScaleRow(label: String, scale: Float, onScaleCommitted: (Float) -> Unit) {
    val palette = KapowTheme.palette
    var dragged by remember(scale) { mutableStateOf(scale) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = RowPadding, vertical = SliderRowVerticalPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            ValuePill(text = dragged.scaleLabel())
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            steps = BUBBLE_SCALE_STEPS,
            valueRange = BUBBLE_SCALE_RANGE,
            onValueChangeFinished = { onScaleCommitted(dragged) },
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.track,
                activeTickColor = palette.onAccent,
                inactiveTickColor = palette.inkFaint,
            ),
            thumb = { Box(modifier = Modifier.size(ThumbSize).clip(CircleShape).background(palette.accent)) },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScaleBound(BUBBLE_SCALE_RANGE.start)
            ScaleBound(BUBBLE_SCALE_RANGE.endInclusive)
        }
    }
}

@Composable
private fun ValuePill(text: String) {
    val palette = KapowTheme.palette
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        fontWeight = FontWeight.SemiBold,
        color = palette.accent,
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.selection)
            .padding(horizontal = PillPaddingHorizontal, vertical = PillPaddingVertical),
    )
}

@Composable
private fun ScaleBound(value: Float) {
    Text(text = value.scaleLabel(), style = MaterialTheme.typography.labelSmall, color = KapowTheme.palette.inkFaint)
}

private fun Float.scaleLabel(): String = "%.1f×".format(this)
