package com.comicify.feature.stats.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.comicify.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

@Composable
private fun numberFormat(): NumberFormat {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

@Composable
fun figure(value: Int): String = numberFormat().format(value)

@Composable
fun figure(value: Float): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 } }
    return format.format(value)
}

data class HoursMinutes(val hours: Int, val minutes: Int)

fun hoursAndMinutes(millis: Long): HoursMinutes {
    val total = (millis / MILLIS_PER_MINUTE).toInt()
    return HoursMinutes(hours = total / MINUTES_PER_HOUR, minutes = total % MINUTES_PER_HOUR)
}

@Composable
fun duration(millis: Long): String {
    val (hours, minutes) = hoursAndMinutes(millis)
    if (hours == 0) return stringResource(R.string.stats_value_minutes, figure(minutes))
    return stringResource(R.string.stats_value_hours_minutes, figure(hours), figure(minutes))
}

@Composable
fun dayLabel(date: LocalDate): String {
    val context = LocalContext.current
    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
    )
}
