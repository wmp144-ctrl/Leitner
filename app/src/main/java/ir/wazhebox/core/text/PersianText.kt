package ir.wazhebox.core.text

import kotlinx.datetime.LocalDate

/** Persian digits, alphabet, and Jalali dates (PRD §18.3 rule 5, §23). */
object PersianText {

    private val PERSIAN_MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    fun toPersianDigits(input: String): String = buildString(input.length) {
        input.forEach { char ->
            if (char in '0'..'9') {
                append('۰' + (char - '0'))
            } else {
                append(char)
            }
        }
    }

    fun number(value: Int): String = toPersianDigits(value.toString())

    fun number(value: Long): String = toPersianDigits(value.toString())

    fun percent(value: Double): String =
        toPersianDigits(((value * 100).toInt()).toString()) + "٪"

    /** Jalali date for display; storage stays Gregorian/UTC. */
    fun jalaliDate(date: LocalDate): String {
        val (year, month, day) = gregorianToJalali(date.year, date.monthNumber, date.dayOfMonth)
        return "${number(day)} ${PERSIAN_MONTHS[month - 1]} ${number(year)}"
    }

    fun jalaliShort(date: LocalDate): String {
        val (year, month, day) = gregorianToJalali(date.year, date.monthNumber, date.dayOfMonth)
        return number(year) + "/" + number(month).padStart(2, '۰') + "/" + number(day).padStart(2, '۰')
    }

    /** jalaali-js algorithm; pure integer math, no dependency. */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]

        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Triple(jy, jm, jd)
    }
}
