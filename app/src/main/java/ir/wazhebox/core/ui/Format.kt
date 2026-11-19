package ir.wazhebox.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/** "امروز / فردا / ۳ روز" for an interval in days. */
@Composable
fun intervalLabel(days: Double): String = when {
    days < 1.0 -> stringResource(R.string.common_today)
    days < 1.5 -> stringResource(R.string.common_tomorrow)
    else -> stringResource(R.string.common_days, PersianText.number(days.roundToInt()))
}

/** Due label for a future instant relative to [now]. */
@Composable
fun dueLabel(dueAt: Instant?, now: Instant): String {
    if (dueAt == null) return stringResource(R.string.common_not_set)
    val days = (dueAt.toEpochMilliseconds() - now.toEpochMilliseconds()).toDouble() / 86_400_000.0
    return when {
        days <= 0.0 -> stringResource(R.string.common_today)
        days < 1.0 -> stringResource(R.string.common_today)
        days < 2.0 -> stringResource(R.string.common_tomorrow)
        else -> stringResource(R.string.common_days, PersianText.number(days.roundToInt()))
    }
}

fun jalaliDayLabel(instant: Instant, timeZone: TimeZone): String =
    PersianText.jalaliDate(instant.toLocalDateTime(timeZone).date)

/** Minutes label for the summary screen. */
@Composable
fun minutesLabel(totalMs: Long): String {
    val minutes = (totalMs / 60_000.0).coerceAtLeast(0.0)
    val rounded = if (minutes < 1.0) 1 else minutes.roundToInt()
    return stringResource(R.string.common_minutes, PersianText.number(rounded))
}
