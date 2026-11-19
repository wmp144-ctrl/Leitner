package ir.wazhebox.domain.grading

import java.text.Normalizer
import java.util.Locale

/**
 * Answer normalization (PRD §11.1).
 *
 * Every rule here exists because of a real Persian/English input quirk:
 * Arabic ي/ك, ZWNJ in می‌روم, Persian digits, invisible marks from copy/paste,
 * smart apostrophes, and stray punctuation around a typed word.
 */
object TextNormalizer {

    // ZWNJ (\u200C) is handled separately: it becomes a space, not nothing.
    private val INVISIBLE = Regex("[\\u200B\\u200D-\\u200F\\uFEFF]")
    private val PERSIAN_DIGITS = Regex("[\\u06F0-\\u06F9]")
    private val ARABIC_INDIC_DIGITS = Regex("[\\u0660-\\u0669]")
    private val WHITESPACE = Regex("\\s+")
    private val EDGE_PUNCTUATION = Regex("^[\\p{Punct}]+|[\\p{Punct}]+$")
    private val CONTROL_CHARS = Regex("[\\p{Cntrl}&&[^\\t\\n\\r]]")
    private val PERSIAN_LETTER = Regex("[\\u0621-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC\\u0647]")
    private val EMOJI = Regex("[\\uD800-\\uDFFF]")

    /** Normalization used for judging typed answers. */
    fun normalize(input: String): String {
        var text = Normalizer.normalize(input, Normalizer.Form.NFKC)
        text = text.replace(INVISIBLE, "")
        text = text.replace('\u200C', ' ')
        text = text.replace('ي', 'ی').replace('ك', 'ک')
        text = text.replace('ۀ', 'ه').replace('أ', 'ا').replace('إ', 'ا')
        text = text.replace(PERSIAN_DIGITS) { match ->
            ('0' + (match.value[0].code - 0x06F0)).toString()
        }
        text = text.replace(ARABIC_INDIC_DIGITS) { match ->
            ('0' + (match.value[0].code - 0x0660)).toString()
        }
        text = text.replace(CONTROL_CHARS, "")
        text = text.replace('\u2019', '\'').replace('\u2018', '\'')
        text = text.replace('\u201C', '"').replace('\u201D', '"')
        text = text.replace('\u2013', '-').replace('\u2014', '-')
        text = text.trim().lowercase(Locale.ROOT)
        text = text.replace(WHITESPACE, " ")
        text = text.replace(EDGE_PUNCTUATION, "")
        return text.trim()
    }

    /**
     * Normalization used for uniqueness and search (`term_norm` in the schema):
     * trim + NFKC + lowercase + apostrophe unification + collapsed spaces.
     */
    fun normalizeKey(term: String): String {
        var text = Normalizer.normalize(term, Normalizer.Form.NFKC)
        text = text.replace('\u2019', '\'').replace('\u2018', '\'')
        text = text.replace('\u200C', ' ')
        return text.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")
    }

    fun hasPersianLetters(input: String): Boolean = PERSIAN_LETTER.containsMatchIn(input)

    fun stripEmoji(input: String): String = input.replace(EMOJI, "")

    /** Separate every meaning of `خانه|منزل` into accepted answers. */
    fun acceptedAnswers(meaningFa: String): List<String> =
        meaningFa.split('|')
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * Optimal-string-alignment distance (Levenshtein + adjacent transpositions).
     *
     * A transposition like `recieve` → `receive` is one edit for a human but two
     * for plain Levenshtein, which would turn a typo into a lapse (PRD §11.4
     * lists `recieve` as TYPO, so transpositions must count as one).
     */
    fun levenshtein(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1

        var twoRowsBack = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost,
                )
                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] &&
                    a[i - 2] == b[j - 1]
                ) {
                    value = minOf(value, twoRowsBack[j - 2] + 1)
                }
                current[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > limit) return limit + 1
            val recycled = twoRowsBack
            twoRowsBack = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }
}
