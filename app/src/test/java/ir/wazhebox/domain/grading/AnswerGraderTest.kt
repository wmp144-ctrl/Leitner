package ir.wazhebox.domain.grading

import com.google.common.truth.Truth.assertThat
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Rating
import ir.wazhebox.domain.model.Verdict
import org.junit.Test

/** Acceptance cases from PRD §11.4 and Appendix B (judging). */
class AnswerGraderTest {

    private fun grade(
        input: String,
        vararg answers: String,
        direction: Direction = Direction.FA_EN,
    ) = AnswerGrader.grade(input, answers.toList(), direction)

    // --- exact matches -------------------------------------------------------

    @Test
    fun `exact answer is correct`() {
        assertThat(grade("color", "color").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `case is ignored`() {
        assertThat(grade("COLOR", "color").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertThat(grade("  color  ", "color").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `inner whitespace runs collapse`() {
        assertThat(grade("run   out\tof", "run out of").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `newline inside answer is tolerated`() {
        assertThat(grade("run out\nof", "run out of").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `trailing punctuation is stripped`() {
        assertThat(grade("color.", "color").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `leading punctuation is stripped`() {
        assertThat(grade("(hello)", "hello").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `quoted answer is accepted`() {
        assertThat(grade("\"quoted\"", "quoted").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `every meaning separated by pipe is accepted`() {
        val accepted = TextNormalizer.acceptedAnswers("خانه|منزل")
        assertThat(AnswerGrader.grade("خانه", accepted, Direction.EN_FA).verdict)
            .isEqualTo(Verdict.CORRECT)
        assertThat(AnswerGrader.grade("منزل", accepted, Direction.EN_FA).verdict)
            .isEqualTo(Verdict.CORRECT)
    }

    // --- typography and unicode ---------------------------------------------

    @Test
    fun `typographic apostrophe matches straight apostrophe`() {
        assertThat(grade("don’t", "don't").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `straight apostrophe matches typographic stored answer`() {
        assertThat(grade("don't", "don’t").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `persian digits match latin digits`() {
        assertThat(grade("۲۰۲۴", "2024").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `arabic yeh is normalized to persian yeh`() {
        assertThat(grade("كتاب", "کتاب", direction = Direction.EN_FA).verdict)
            .isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `zwnj and space are equivalent`() {
        assertThat(TextNormalizer.normalize("می‌روم")).isEqualTo(TextNormalizer.normalize("می روم"))
    }

    @Test
    fun `invisible marks are removed`() {
        assertThat(grade("co\u200Blor\uFEFF", "color").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `emoji inside answer does not break the match`() {
        assertThat(TextNormalizer.normalize("color😀").contains("color")).isTrue()
    }

    @Test
    fun `internal apostrophe and hyphen are preserved`() {
        assertThat(TextNormalizer.normalize("well-known")).isEqualTo("well-known")
        assertThat(TextNormalizer.normalize("don't")).isEqualTo("don't")
    }

    // --- typos ---------------------------------------------------------------

    @Test
    fun `one letter slip in a long word is a typo`() {
        val result = grade("recieve", "receive")
        assertThat(result.verdict).isEqualTo(Verdict.TYPO)
        assertThat(result.suggestedRating).isEqualTo(Rating.GOOD)
        assertThat(result.matchedAnswer).isEqualTo("receive")
    }

    @Test
    fun `transposition in a short word is a typo`() {
        assertThat(grade("thier", "their").verdict).isEqualTo(Verdict.TYPO)
    }

    @Test
    fun `two slips in a very long word is still a typo`() {
        assertThat(grade("accomodaton", "accommodation").verdict).isEqualTo(Verdict.TYPO)
    }

    @Test
    fun `two slips in a medium word is wrong`() {
        assertThat(grade("colrxx", "color").verdict).isEqualTo(Verdict.WRONG)
    }

    @Test
    fun `different word of similar length is wrong`() {
        assertThat(grade("go", "going").verdict).isEqualTo(Verdict.WRONG)
    }

    @Test
    fun `a typo never costs an interval`() {
        assertThat(AnswerGrader.ratingForVerdict(Verdict.TYPO)).isEqualTo(Rating.GOOD)
    }

    @Test
    fun `wrong answer schedules again`() {
        assertThat(AnswerGrader.ratingForVerdict(Verdict.WRONG)).isEqualTo(Rating.AGAIN)
    }

    @Test
    fun `revealed answer schedules again`() {
        assertThat(AnswerGrader.ratingForVerdict(Verdict.REVEALED)).isEqualTo(Rating.AGAIN)
    }

    // --- keyboard mistakes ---------------------------------------------------

    @Test
    fun `persian typed into an english answer is a keyboard mistake`() {
        assertThat(grade("کتاب", "book").verdict).isEqualTo(Verdict.WRONG_KEYBOARD)
    }

    @Test
    fun `keyboard mistake does not change scheduling`() {
        val result = grade("کتاب", "book")
        assertThat(result.affectsSchedule).isFalse()
        assertThat(AnswerGrader.ratingForVerdict(Verdict.WRONG_KEYBOARD)).isNull()
    }

    @Test
    fun `persian answer in en_fa direction is not a keyboard mistake`() {
        val accepted = TextNormalizer.acceptedAnswers("کتاب")
        assertThat(AnswerGrader.grade("کتاب", accepted, Direction.EN_FA).verdict)
            .isEqualTo(Verdict.CORRECT)
    }

    // --- empty and skipped ---------------------------------------------------

    @Test
    fun `empty input is skipped`() {
        assertThat(grade("", "color").verdict).isEqualTo(Verdict.SKIPPED)
    }

    @Test
    fun `blank input is skipped`() {
        assertThat(grade("   \n ", "color").verdict).isEqualTo(Verdict.SKIPPED)
    }

    @Test
    fun `skipped does not affect scheduling`() {
        assertThat(grade("", "color").affectsSchedule).isFalse()
    }

    @Test
    fun `punctuation only input is skipped`() {
        assertThat(grade("!!!", "color").verdict).isEqualTo(Verdict.SKIPPED)
    }

    // --- normalization invariants -------------------------------------------

    @Test
    fun `normalization is idempotent`() {
        val once = TextNormalizer.normalize("  Color  ")
        assertThat(TextNormalizer.normalize(once)).isEqualTo(once)
    }

    @Test
    fun `judging is stable under repeated normalization`() {
        val first = grade("recieve", "receive")
        val second = AnswerGrader.grade(
            TextNormalizer.normalize("recieve"),
            listOf("receive"),
            Direction.FA_EN,
        )
        assertThat(second.verdict).isEqualTo(first.verdict)
    }

    @Test
    fun `british spelling is a typo unless registered`() {
        // Appendix B case 6 expects WRONG here, but §11.2's own length rule
        // (5..8 letters, distance 1 → TYPO) classifies it as TYPO.
        // The algorithm wins and the conflict is recorded in docs/ADR.md.
        assertThat(grade("Colour", "color").verdict).isEqualTo(Verdict.TYPO)
        assertThat(grade("Colour", "color", "colour").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `mixed english persian input is judged as english`() {
        // Persian letters present but the answer itself is English: keyboard hint
        // still wins, because a Persian keyboard is the likely cause.
        assertThat(grade("خانه", "home").verdict).isEqualTo(Verdict.WRONG_KEYBOARD)
    }

    @Test
    fun `multi word answer with extra spaces is correct`() {
        assertThat(grade("run    out   of", "run out of").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `hyphenated word keeps its hyphen`() {
        assertThat(grade("e-mail", "e-mail").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `covid with digits is judged correctly`() {
        assertThat(grade("COVID-19", "covid-19").verdict).isEqualTo(Verdict.CORRECT)
    }

    @Test
    fun `accepted answers are deduplicated`() {
        val accepted = TextNormalizer.acceptedAnswers("home|Home|home")
        assertThat(accepted).containsExactly("home")
    }

    @Test
    fun `levenshtein distance is symmetric`() {
        assertThat(TextNormalizer.levenshtein("color", "colour"))
            .isEqualTo(TextNormalizer.levenshtein("colour", "color"))
    }

    @Test
    fun `levenshtein early exit respects the limit`() {
        assertThat(TextNormalizer.levenshtein("abcdefgh", "zzzzzzzz", limit = 2)).isGreaterThan(2)
    }

    @Test
    fun `persian letter detection`() {
        assertThat(TextNormalizer.hasPersianLetters("خانه")).isTrue()
        assertThat(TextNormalizer.hasPersianLetters("house")).isFalse()
        assertThat(TextNormalizer.hasPersianLetters("۲۰۲۴")).isFalse()
    }
}
