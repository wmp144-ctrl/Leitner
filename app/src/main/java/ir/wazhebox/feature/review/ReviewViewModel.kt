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
    private val undoStack = ArrayDeque<Pair<StudyItem, GradeOutcome>>()
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
        val items = studyRepository.buildSession(freePractice).toMutableList()
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
        val accepted = current.word.meanings
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
        val current = _state.value.current ?: return
        val typedAnswer = _state.value.typed
        val verdict = _state.value.grade?.verdict
        val duration = if (questionStartedAt == 0L) 0L
        else System.currentTimeMillis() - questionStartedAt

        if (freePractice) {
            // Free practice must not touch the schedule (PRD §9.4 rule 6).
            advance(current, countCorrect = rating != Rating.AGAIN, durationMs = duration)
            return
        }

        viewModelScope.launch {
            val outcome = studyRepository.grade(
                cardId = current.card.id,
                rating = rating,
                verdict = verdict,
                answerText = typedAnswer.takeIf { it.isNotBlank() },
                durationMs = duration,
            )
            if (outcome == null) {
                _state.value = _state.value.copy(saveError = true)
                return@launch
            }
            undoStack.addLast(current to outcome).also {
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
        )
        if (!finished) showNext()
    }

    private fun showNext() {
        val next = queue.firstOrNull()
        if (next == null) {
            _state.value = _state.value.copy(finished = true, current = null, remaining = 0)
            return
        }
        val settings = settingsSnapshot
        val previews = Rating.entries.associateWith { rating ->
            Scheduler.schedule(
                card = next.card,
                rating = rating,
                now = kotlinx.datetime.Clock.System.now(),
                config = settings,
            ).intervalDays
        }
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

    private suspend fun loadSettings() {
        val settings = settingsRepository.settings.first()
        settingsSnapshot = SchedulerConfig(
            desiredRetention = settings.desiredRetention,
            maximumIntervalDays = settings.maximumIntervalDays,
        )
        _state.value = _state.value.copy(hapticsEnabled = settings.hapticsEnabled)
    }

    fun undo() {
        val (undoItem, outcome) = undoStack.removeLastOrNull() ?: return
        viewModelScope.launch {
            studyRepository.undo(outcome)
            val restored = undoItem.copy(card = outcome.cardBefore)
            queue.addFirst(restored)
            _state.value = _state.value.copy(
                current = restored,
                remaining = queue.size,
                reviewed = (_state.value.reviewed - 1).coerceAtLeast(0),
                correct = _state.value.correct.coerceAtLeast(0),
                canUndo = undoStack.isNotEmpty(),
                showAnswer = false,
                typed = "",
                grade = null,
                finished = false,
                leechWordId = null,
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
