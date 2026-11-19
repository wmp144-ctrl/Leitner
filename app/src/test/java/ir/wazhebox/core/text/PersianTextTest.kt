package ir.wazhebox.core.text

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test

class PersianTextTest {

    @Test
    fun `digits become persian`() {
        assertThat(PersianText.toPersianDigits("2026")).isEqualTo("۲۰۲۶")
    }

    @Test
    fun `latin text is untouched`() {
        assertThat(PersianText.toPersianDigits("abc-12")).isEqualTo("abc-۱۲")
    }

    @Test
    fun `jalali conversion for nowruz 2026`() {
        assertThat(PersianText.gregorianToJalali(2026, 3, 21)).isEqualTo(Triple(1405, 1, 1))
    }

    @Test
    fun `jalali conversion for nowruz 2024`() {
        assertThat(PersianText.gregorianToJalali(2024, 3, 20)).isEqualTo(Triple(1403, 1, 1))
    }

    @Test
    fun `day after nowruz advances the day`() {
        assertThat(PersianText.gregorianToJalali(2026, 3, 22)).isEqualTo(Triple(1405, 1, 2))
    }

    @Test
    fun `jalali date is formatted in persian`() {
        val formatted = PersianText.jalaliDate(LocalDate(2026, 3, 21))
        assertThat(formatted).isEqualTo("۱ فروردین ۱۴۰۵")
    }

    @Test
    fun `percent uses persian percent sign`() {
        assertThat(PersianText.percent(0.9)).isEqualTo("۹۰٪")
    }
}
