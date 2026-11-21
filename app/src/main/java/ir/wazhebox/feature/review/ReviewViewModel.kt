package ir.wazhebox.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.data.tts.TtsController
import ir.wazhebox.domain.grading.AnswerGrader
import ir.wazhebox.domain.grading.GradeResult
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Rating
import ir.wazhebox.domain.model.StudyItem
import ir.wazhebox.domain.model.Verdict
import ir.wazhebox.domain.repo.GradeOutcome
import ir.wazhebox.domain.repo.StudyRepository
import ir.wazhebox.domain.scheduler.Scheduler
import ir.wazhebox.domain.scheduler.SchedulerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val loading: Boolean = true,
    val freePractice: Boolean = false,
    val current: StudyItem? = null,
    val remaining: Int = 0,
    val showAnswer: Boolean = false,
    val typed: String = "",
    val grade: GradeResult? = null,
    val intervalPreviews: Map<Rating, Double> = emptyMap(),
    val reviewed: Int = 0,
    val correct: Int = 0,
    val elapsedMs: Long = 0,
    val canUndo: Boolean = false,
    val finished: Boolean = false,
    val leechWordId: Long? = null,
    val saveError: Boolean = false,
    val clockAnomaly: Boolean = false,
    val empty: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val swipeEnabled: Boolean = false,
    /** A rating is being written; further taps are ignored, not graded twice. */
    val saving: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository,
    private val tts: TtsController,
    private val widgetUpdater: ir.wazhebox.widget.WidgetUpdater,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val freePractice: Boolean =
        savedStateHandle.get<Boolean>("freePractice") ?: false

    private val _state = MutableStateFlow(ReviewUiState(freePractice = freePractice))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val queue = ArrayDeque<StudyItem>()
    /** Undo needs the rating too, otherwise the accuracy counter cannot be rolled back. */
    private data class UndoEntry(
        val item: StudyItem,
        val outcome: GradeOutcome,
        val countedCorrect: Boolean,
    )

    private val undoStack = ArrayDeque<UndoEntry>()
    private var questionStartedAt: Long = 0
    private var sessionStartedAt: Long = 0

    private val ttsStatus = tts.status

    init {
        viewModelScope.launch {
            loadSettings()
            load()
        }
    }

    private suspend fun load() {
        // A failed query used to leave the spinner up forever; an empty session is
        // a recoverable, visible state.
        val items = runCatching { studyRepository.buildSession(freePractice) }
            .getOrDefault(emptyList())
            .toMutableList()
        queue.clear()
        queue.addAll(items)
        sessionStartedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(
            loading = false,
            empty = queue.isEmpty(),
            finished = queue.isEmpty(),
            remaining = queue.size,
        )
        showNext()
    }

    fun typed(text: String) {
        _state.value = _state.value.copy(typed = text)
    }

    fun checkAnswer() {
        val current = _state.value.current ?: return
        // FA_EN asks for the English word and shows the Persian meaning, so the
        // answers it accepts are the term itself. Comparing against the meanings
        // (as the old version did) marked every reverse answer wrong.
        val accepted = if (current.card.direction == Direction.FA_EN) {
            listOf(current.word.term)
        } else {
            current.word.meanings
        }
        val result = AnswerGrader.grade(
            input = _state.value.typed,
            acceptedAnswers = accepted,
            direction = current.card.direction,
        )
        // The rating buttons stay visible after a correct answer: nothing is
        // auto-graded, the learner can still pick "easy" or "hard" (§12.2).
        _state.value = _state.value.copy(
            showAnswer = result.verdict != Verdict.SKIPPED,
            grade = result,
        )
    }

    /** Flips the card back to the question without grading it. */
    fun hideAnswer() {
        _state.value = _state.value.copy(showAnswer = false, grade = null)
    }

    fun reveal() {
        _state.value = _state.value.copy(
            showAnswer = true,
            grade = GradeResult(
                verdict = Verdict.REVEALED,
                normalizedInput = "",
                suggestedRating = Rating.AGAIN,
            ),
        )
    }

    fun rate(rating: Rating) {
        // Tapping two ratings in a row used to write two reviews for one card:
        // the queue only moved on after the first write finished.
        if (_state.value.saving) return
        val current = _state.value.current ?: return
        val typedAnswer = _state.value.typed
        val verdict = _state.value.grade?.verdict
        val duration = if (questionStartedAt == 0L) 0L
        else System.currentTimeMillis() - questionStartedAt

        if (freePractice) {
            // Free practice must not touch the schedule (PRD §9.4 rule 6).
            _state.value = _state.value.copy(saving = true)
            advance(current, countCorrect = rating != Rating.AGAIN, durationMs = duration)
            return
        }

        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val outcome = studyRepository.grade(
                cardId = current.card.id,
                rating = rating,
                verdict = verdict,
                answerText = typedAnswer.takeIf { it.isNotBlank() },
                durationMs = duration,
            )
            if (outcome == null) {
                _state.value = _state.value.copy(saveError = true, saving = false)
                return@launch
            }
            undoStack.addLast(
                UndoEntry(
                    item = current,
                    outcome = outcome,
                    countedCorrect = rating != Rating.AGAIN,
                ),
            ).also {
                while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            }
            val requeue = outcome.card.state == CardState.LEARNING ||
                outcome.card.state == CardState.RELEARNING
            advance(
                current = current,
                countCorrect = rating != Rating.AGAIN,
                durationMs = duration,
                requeued = if (requeue) current.copy(card = outcome.card) else null,
                leech = if (outcome.leechSuggestion) current.word.id else null,
                clockAnomaly = outcome.clockAnomaly,
            )
        }
    }

    private fun advance(
        current: StudyItem,
        countCorrect: Boolean,
        durationMs: Long,
        requeued: StudyItem? = null,
        leech: Long? = null,
        clockAnomaly: Boolean = false,
    ) {
        queue.removeFirstOrNull()
        requeued?.let { queue.addLast(it) }
        val reviewed = _state.value.reviewed + 1
        val correct = _state.value.correct + if (countCorrect) 1 else 0
        val finished = queue.isEmpty()
        if (finished) viewModelScope.launch { runCatching { widgetUpdater.refresh() } }
        _state.value = _state.value.copy(
            reviewed = reviewed,
            correct = correct,
            elapsedMs = System.currentTimeMillis() - sessionStartedAt,
            canUndo = undoStack.isNotEmpty(),
            remaining = queue.size,
            finished = finished,
            leechWordId = leech,
            clockAnomaly = clockAnomaly,
            saveError = false,
            saving = false,
        )
        if (!finished) showNext()
    }

    private fun showNext() {
        val next = queue.firstOrNull()
        if (next == null) {
            _state.value = _state.value.copy(finished = true, current = null, remaining = 0)
            return
        }
        val previews = previewsFor(next)
        questionStartedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(
            current = next,
            showAnswer = false,
            typed = "",
            grade = null,
            intervalPreviews = previews,
            remaining = queue.size,
        )
    }

    /** Scheduler config is read once per queue load; changing it mid-session is out of scope. */
    private var settingsSnapshot: SchedulerConfig = SchedulerConfig()

    /** Interval shown on each rating tile for the given card. */
    private fun previewsFor(item: StudyItem): Map<Rating, Double> =
        Rating.entries.associateWith { rating ->
            Scheduler.schedule(
                card = item.card,
                rating = rating,
                now = kotlinx.datetime.Clock.System.now(),
                config = settingsSnapshot,
            ).intervalDays
        }

    private suspend fun loadSettings() {
        val settings = settingsRepository.settings.first()
        settingsSnapshot = SchedulerConfig(
            desiredRetention = settings.desiredRetention,
            maximumIntervalDays = settings.maximumIntervalDays,
        )
        _state.value = _state.value.copy(
            hapticsEnabled = settings.hapticsEnabled,
            swipeEnabled = settings.swipeEnabled,
        )
    }

    fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        viewModelScope.launch {
            studyRepository.undo(entry.outcome)
            val restored = entry.item.copy(card = entry.outcome.cardBefore)
            // A card that went back to a learning step was requeued at the end;
            // restoring it at the front would have shown the same card twice.
            queue.removeAll { it.card.id == restored.card.id }
            queue.addFirst(restored)
            _state.value = _state.value.copy(
                current = restored,
                intervalPreviews = previewsFor(restored),
                remaining = queue.size,
                reviewed = (_state.value.reviewed - 1).coerceAtLeast(0),
                // Undoing a correct answer must also take it back out of the
                // accuracy shown on the summary; it used to stay counted.
                correct = (_state.value.correct - if (entry.countedCorrect) 1 else 0)
                    .coerceAtLeast(0),
                canUndo = undoStack.isNotEmpty(),
                showAnswer = false,
                typed = "",
                grade = null,
                finished = false,
                leechWordId = null,
                saving = false,
            )
            questionStartedAt = System.currentTimeMillis()
        }
    }

    fun speak() {
        val word = _state.value.current?.word ?: return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            tts.speak(word.term, settings.speechRate)
        }
    }

    fun dismissLeech() {
        _state.value = _state.value.copy(leechWordId = null)
    }

    fun dismissSaveError() {
        _state.value = _state.value.copy(saveError = false)
    }

    val ttsAvailable: StateFlow<ir.wazhebox.data.tts.TtsStatus> = ttsStatus

    init {
        tts.checkAvailability()
    }

    override fun onCleared() {
        tts.shutdown()
        super.onCleared()
    }

    private companion object {
        const val MAX_UNDO = 20
    }
}

/** Direction helpers used by the review screen. */
val Direction.isTypedAnswer: Boolean
    get() = this == Direction.FA_EN
