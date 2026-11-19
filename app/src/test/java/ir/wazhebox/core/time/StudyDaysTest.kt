package ir.wazhebox.core.time

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test

class StudyDaysTest {

    private val tehran = TimeZone.of("Asia/Tehran")

    @Test
    fun `review at 23 59 and 00 01 share a study day`() {
        val late = Instant.parse("2026-11-19T20:29:00Z") // 23:59 Tehran
        val early = Instant.parse("2026-11-19T20:31:00Z") // 00:01 Tehran
        assertThat(StudyDays.studyDayOf(late, tehran))
            .isEqualTo(StudyDays.studyDayOf(early, tehran))
    }

    @Test
    fun `review at 03 59 still belongs to the previous day`() {
        val beforeStart = Instant.parse("2026-11-19T00:29:00Z") // 03:59 Tehran
        assertThat(StudyDays.studyDayOf(beforeStart, tehran))
            .isEqualTo(LocalDate(2026, 11, 18))
    }

    @Test
    fun `review at 04 01 starts a new day`() {
        val afterStart = Instant.parse("2026-11-19T00:31:00Z") // 04:01 Tehran
        assertThat(StudyDays.studyDayOf(afterStart, tehran))
            .isEqualTo(LocalDate(2026, 11, 19))
    }

    @Test
    fun `days between uses calendar days not milliseconds`() {
        val from = Instant.parse("2026-03-21T12:00:00Z")
        val to = Instant.parse("2026-03-23T12:00:00Z")
        assertThat(StudyDays.studyDaysBetween(from, to, tehran)).isEqualTo(2)
    }

    @Test
    fun `days between is never negative`() {
        val from = Instant.parse("2026-11-20T12:00:00Z")
        val to = Instant.parse("2026-11-19T12:00:00Z")
        assertThat(StudyDays.studyDaysBetween(from, to, tehran)).isEqualTo(0)
    }

    @Test
    fun `start of study day round trips`() {
        val date = LocalDate(2026, 11, 19)
        val start = StudyDays.startOfStudyDay(date, tehran)
        assertThat(StudyDays.studyDayOf(start, tehran)).isEqualTo(date)
    }

    @Test
    fun `custom start hour is honoured`() {
        val noon = Instant.parse("2026-11-19T08:30:00Z") // 12:00 Tehran
        assertThat(StudyDays.studyDayOf(noon, tehran, startHour = 18))
            .isEqualTo(LocalDate(2026, 11, 18))
    }
}
