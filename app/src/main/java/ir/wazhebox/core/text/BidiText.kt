package ir.wazhebox.core.text

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import ir.wazhebox.core.ui.theme.Inter

/**
 * Bidi helpers (PRD §18.3). English words inside Persian sentences must be
 * isolated, or punctuation jumps to the wrong side.
 */
object BidiText {

    private const val LRI = '\u2066'
    private const val PDI = '\u2069'

    /** Wrap LTR text (a word, a quote, `COVID-19`) in a Unicode isolate. */
    fun isolateLtr(text: String): String = "$LRI$text$PDI"

    /** English-only style: Inter, LTR, English locale. */
    fun englishStyle(base: TextStyle = TextStyle()): TextStyle = base.copy(
        fontFamily = Inter,
        localeList = LocaleList("en-US"),
        textDirection = TextDirection.Ltr,
    )
}
