package ir.wazhebox.core.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Injectable clock so scheduling and "today" are testable (PRD §7.1). */
interface StudyClock {
    fun now(): Instant
    fun timeZone(): TimeZone
}

class SystemStudyClock : StudyClock {
    override fun now(): Instant = Clock.System.now()
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}

/**
 * "Study day" boundaries (PRD §9.4 rules 2–3).
 *
 * The day starts at 04:00 by default, so a 23:59 review and a 00:01 review land
 * on the same day, and DST/time-zone changes are handled by calendar arithmetic
 * instead of dividing milliseconds by 86_400_000.
 */
object StudyDays {

    const val DEFAULT_START_HOUR = 4

    fun studyDayOf(
        instant: Instant,
        timeZone: TimeZone,
        startHour: Int = DEFAULT_START_HOUR,
    ): LocalDate {
        val local = instant.toLocalDateTime(timeZone)
        return if (local.hour < startHour) {
            local.date.minus(1, DateTimeUnit.DAY)
        } else {
            local.date
        }
    }

    fun startOfStudyDay(
        date: LocalDate,
        timeZone: TimeZone,
        startHour: Int = DEFAULT_START_HOUR,
    ): Instant = date.atTime(LocalTime(startHour, 0)).toInstant(timeZone)

    /** Whole study days between two instants (never negative). */
    fun studyDaysBetween(
        from: Instant,
        to: Instant,
        timeZone: TimeZone,
        startHour: Int = DEFAULT_START_HOUR,
    ): Int {
        val fromDay = studyDayOf(from, timeZone, startHour)
        val toDay = studyDayOf(to, timeZone, startHour)
        return fromDay.daysUntil(toDay).coerceAtLeast(0)
    }
}
