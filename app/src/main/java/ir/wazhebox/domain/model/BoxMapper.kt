package ir.wazhebox.domain.model

/**
 * Maps FSRS stability to the five learner-facing boxes plus "settled" (PRD §5.1).
 *
 * The user sees a Lichtner-style box, but the number is derived from stability,
 * so intervals stay adaptive instead of the classic fixed 1/2/4/8/16 days.
 */
object BoxMapper {

    /** Upper stability bounds (days) for boxes 1..5; above that the card is box 6. */
    private val UPPER_BOUNDS_DAYS = doubleArrayOf(1.0, 3.0, 7.0, 21.0, 60.0)

    const val MAX_BOX = 6

    fun boxOf(state: CardState, stability: Double): Int {
        if (state == CardState.NEW) return 1
        if (state == CardState.LEARNING || state == CardState.RELEARNING) return 1
        UPPER_BOUNDS_DAYS.forEachIndexed { index, bound ->
            if (stability < bound) return index + 1
        }
        return MAX_BOX
    }

    /** True for box 6 — the learner-facing "settled" state. */
    fun isSettled(box: Int): Boolean = box >= MAX_BOX
}
