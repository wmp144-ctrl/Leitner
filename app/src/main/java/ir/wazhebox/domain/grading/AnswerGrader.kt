package ir.wazhebox.domain.grading

import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Rating
import ir.wazhebox.domain.model.Verdict

data class GradeResult(
    val verdict: Verdict,
    val normalizedInput: String,
    val matchedAnswer: String? = null,
    val distance: Int? = null,
    val suggestedRating: Rating? = null,
) {
    /** Whether this verdict should change scheduling at all (PRD §11.3). */
    val affectsSchedule: Boolean
        get() = verdict != Verdict.SKIPPED && verdict != Verdict.WRONG_KEYBOARD
}

/**
 * Judging engine for typed answers (PRD §11).
 *
 * A spelling slip inside a word the learner clearly knows must never cost a
 * review interval, so typos grade as Good while still being recorded as `typo`
 * for the stats; only a real wrong answer schedules Again.
 */
object AnswerGrader {

    fun grade(
        input: String,
        acceptedAnswers: List<String>,
        direction: Direction,
    ): GradeResult {
        if (input.isBlank()) {
            return GradeResult(Verdict.SKIPPED, normalizedInput = "")
        }

        val normalized = TextNormalizer.normalize(input)
        if (normalized.isEmpty()) {
            return GradeResult(Verdict.SKIPPED, normalizedInput = "")
        }

        if (direction == Direction.FA_EN && TextNormalizer.hasPersianLetters(input)) {
            return GradeResult(Verdict.WRONG_KEYBOARD, normalizedInput = normalized)
        }

        val accepted = acceptedAnswers
            .map { TextNormalizer.normalize(it) }
            .filter { it.isNotEmpty() }

        accepted.firstOrNull { it == normalized }?.let { match ->
            return GradeResult(
                verdict = Verdict.CORRECT,
                normalizedInput = normalized,
                matchedAnswer = match,
                distance = 0,
                suggestedRating = Rating.GOOD,
            )
        }

        var nearest: String? = null
        var bestDistance = Int.MAX_VALUE
        accepted.forEach { answer ->
            val distance = TextNormalizer.levenshtein(normalized, answer)
            if (distance < bestDistance) {
                bestDistance = distance
                nearest = answer
            }
        }

        val typo = nearest != null && isTypo(normalized, nearest!!, bestDistance)
        return if (typo) {
            GradeResult(
                verdict = Verdict.TYPO,
                normalizedInput = normalized,
                matchedAnswer = nearest,
                distance = bestDistance,
                suggestedRating = Rating.GOOD,
            )
        } else {
            GradeResult(
                verdict = Verdict.WRONG,
                normalizedInput = normalized,
                matchedAnswer = accepted.firstOrNull(),
                distance = bestDistance.takeIf { it != Int.MAX_VALUE },
                suggestedRating = Rating.AGAIN,
            )
        }
    }

    /** PRD §11.2 step 5: distance rules by answer length. */
    private fun isTypo(input: String, answer: String, distance: Int): Boolean {
        val shortest = minOf(input.length, answer.length)
        return when {
            shortest < 5 -> distance == 0
            shortest < 9 -> distance == 1
            else -> distance <= 2
        }
    }

    fun ratingForVerdict(verdict: Verdict): Rating? = when (verdict) {
        Verdict.CORRECT, Verdict.TYPO -> Rating.GOOD
        Verdict.WRONG, Verdict.REVEALED -> Rating.AGAIN
        Verdict.WRONG_KEYBOARD, Verdict.SKIPPED -> null
    }
}
