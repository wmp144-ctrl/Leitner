package ir.wazhebox.domain.session

import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.StudyItem
import kotlinx.datetime.Instant

/**
 * Queue construction (PRD §12.1).
 *
 * Learning cards that are due come first and uncapped, then due reviews up to
 * the daily limit, then new cards — but new cards are spread through the queue
 * instead of dumped at the front (interleaving).
 */
data class SessionConfig(
    val dailyReviewLimit: Int = 200,
    val dailyNewLimit: Int = 10,
    val newIntroducedToday: Int = 0,
    val freePractice: Boolean = false,
    val freePracticeLimit: Int = 50,
)

object SessionBuilder {

    fun buildQueue(items: List<StudyItem>, now: Instant, config: SessionConfig): List<StudyItem> {
        if (config.freePractice) {
            return items
                .asSequence()
                .filter { it.card.state != CardState.SUSPENDED }
                .sortedWith(compareBy({ it.card.dueAt }, { it.card.id }))
                .take(config.freePracticeLimit)
                .toList()
        }

        val learning = items
            .filter {
                (it.card.state == CardState.LEARNING || it.card.state == CardState.RELEARNING) &&
                    it.card.dueAt <= now
            }
            .sortedBy { it.card.dueAt }

        val reviews = items
            .filter { it.card.state == CardState.REVIEW && it.card.dueAt <= now }
            .sortedBy { it.card.dueAt }
            .let { list -> if (config.dailyReviewLimit <= 0) list else list.take(config.dailyReviewLimit) }

        val newRemaining = (config.dailyNewLimit - config.newIntroducedToday).coerceAtLeast(0)
        val newCards = items
            .filter { it.card.state == CardState.NEW }
            .sortedWith(compareBy({ it.word.createdAt }, { it.card.id }))
            .let { list -> if (newRemaining == 0) emptyList() else list.take(newRemaining) }

        return learning + interleave(reviews, newCards)
    }

    /**
     * Spread [newCards] evenly through [reviews] so they are not front-loaded.
     * The first card is always a review (or a learning card) when reviews exist.
     */
    fun interleave(reviews: List<StudyItem>, newCards: List<StudyItem>): List<StudyItem> {
        if (newCards.isEmpty()) return reviews
        if (reviews.isEmpty()) return newCards
        val total = reviews.size + newCards.size
        val slots = newCards.indices
            .map { (it + 1) * total / (newCards.size + 1) }
            .filter { it in 2..total }
            .distinct()
        val newSlots = slots.toSet()
        val result = ArrayList<StudyItem>(total)
        var reviewIndex = 0
        var newIndex = 0
        for (position in 1..total) {
            val placeNew = newIndex < newCards.size && position in newSlots
            if (placeNew) {
                result += newCards[newIndex++]
            } else if (reviewIndex < reviews.size) {
                result += reviews[reviewIndex++]
            } else if (newIndex < newCards.size) {
                result += newCards[newIndex++]
            }
        }
        return result
    }

    /** Count of cards a "start review" button will actually show. */
    fun dueCount(items: List<StudyItem>, now: Instant, config: SessionConfig): Int {
        if (config.freePractice) {
            return items.count { it.card.state != CardState.SUSPENDED }
        }
        val learning = items.count {
            (it.card.state == CardState.LEARNING || it.card.state == CardState.RELEARNING) &&
                it.card.dueAt <= now
        }
        val reviewCount = items.count { it.card.state == CardState.REVIEW && it.card.dueAt <= now }
            .let { if (config.dailyReviewLimit <= 0) it else minOf(it, config.dailyReviewLimit) }
        val newCount = items.count { it.card.state == CardState.NEW }
            .let { minOf(it, (config.dailyNewLimit - config.newIntroducedToday).coerceAtLeast(0)) }
        return learning + reviewCount + newCount
    }

    /** Next due moment across all live cards, for the "next review" line. */
    fun nextDue(items: List<StudyItem>): Instant? = items
        .asSequence()
        .filter { it.card.state != CardState.SUSPENDED && it.card.state != CardState.NEW }
        .map { it.card.dueAt }
        .minOrNull()
}
