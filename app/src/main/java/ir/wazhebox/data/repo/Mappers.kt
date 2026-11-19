package ir.wazhebox.data.repo

import ir.wazhebox.data.db.CardEntity
import ir.wazhebox.data.db.DeckEntity
import ir.wazhebox.data.db.ReviewEntity
import ir.wazhebox.data.db.WordEntity
import ir.wazhebox.data.db.WordWithCardsRow
import ir.wazhebox.domain.model.Card
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Deck
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Rating
import ir.wazhebox.domain.model.ReviewLog
import ir.wazhebox.domain.model.Verdict
import ir.wazhebox.domain.model.Word
import ir.wazhebox.domain.model.WordSource
import ir.wazhebox.domain.model.WordWithCards
import kotlinx.datetime.Instant

fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

fun Instant.toEpochMillis(): Long = toEpochMilliseconds()

fun DeckEntity.toDomain(): Deck = Deck(
    id = id,
    name = name,
    description = description,
    isDefault = isDefault,
    archived = archived,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun WordEntity.toDomain(): Word = Word(
    id = id,
    deckId = deckId,
    term = term,
    termNorm = termNorm,
    meaningFa = meaningFa,
    pos = pos,
    ipa = ipa,
    exampleEn = exampleEn,
    exampleFa = exampleFa,
    notes = notes,
    source = runCatching { WordSource.valueOf(source) }.getOrDefault(WordSource.MANUAL),
    suspended = suspended,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun CardEntity.toDomain(): Card = Card(
    id = id,
    wordId = wordId,
    direction = Direction.valueOf(direction),
    state = CardState.valueOf(state),
    dueAt = dueAt.toInstant(),
    stability = stability,
    difficulty = difficulty,
    stepIndex = stepIndex,
    reps = reps,
    lapses = lapses,
    lastReviewAt = lastReviewAt?.toInstant(),
    createdAt = createdAt.toInstant(),
)

fun Card.toEntity(): CardEntity = CardEntity(
    id = id,
    wordId = wordId,
    direction = direction.name,
    state = state.name,
    dueAt = dueAt.toEpochMillis(),
    stability = stability,
    difficulty = difficulty,
    stepIndex = stepIndex,
    reps = reps,
    lapses = lapses,
    lastReviewAt = lastReviewAt?.toEpochMillis(),
    createdAt = createdAt.toEpochMillis(),
)

fun ReviewEntity.toDomain(): ReviewLog = ReviewLog(
    id = id,
    cardId = cardId,
    reviewedAt = reviewedAt.toInstant(),
    rating = Rating.fromGrade(rating),
    answerText = answerText,
    verdict = verdict?.let { runCatching { Verdict.valueOf(it) }.getOrNull() },
    durationMs = durationMs,
    prevState = CardState.valueOf(prevState),
    prevStability = prevStability,
    prevDifficulty = prevDifficulty,
    nextState = CardState.valueOf(nextState),
    nextStability = nextStability,
    nextDifficulty = nextDifficulty,
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    undone = undone,
)

fun WordWithCardsRow.toDomain(): WordWithCards = WordWithCards(
    word = word.toDomain(),
    cards = cards.map { it.toDomain() },
)
