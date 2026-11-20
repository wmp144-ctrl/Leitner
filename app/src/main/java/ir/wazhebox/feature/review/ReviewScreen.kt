package ir.wazhebox.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.wazhebox.R
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.ui.components.BoxBadge
import ir.wazhebox.core.ui.components.EmptyState
import ir.wazhebox.core.ui.components.LoadingBox
import ir.wazhebox.core.ui.intervalLabel
import ir.wazhebox.core.ui.minutesLabel
import ir.wazhebox.core.ui.theme.EnglishWordStyle
import ir.wazhebox.core.ui.theme.Inter
import ir.wazhebox.core.ui.theme.PersianMeaningStyle
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.core.text.BidiText
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Rating
import ir.wazhebox.domain.model.Verdict

@Composable
fun ReviewScreen(
    onExit: () -> Unit,
    onFinished: () -> Unit,
    onOpenWord: (Long) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !state.finished) { showExitDialog = true }

    if (state.loading) {
        LoadingBox()
        return
    }

    if (state.finished || state.empty) {
        SessionSummary(
            reviewed = state.reviewed,
            correct = state.correct,
            elapsedMs = state.elapsedMs,
            freePractice = state.freePractice,
            onDone = onFinished,
            onExit = onExit,
        )
        return
    }

    val current = state.current ?: return
    val direction = current.card.direction
    val word = current.word

    // Swipe gestures (PRD §12.2, opt-in): right reveals the answer, left fails
    // the card once its answer is visible. The card follows the finger.
    var dragOffset by remember(current.card.id) { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun tickHaptics() {
        if (state.hapticsEnabled) {
            hapticFeedback.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .pointerInput(state.swipeEnabled, state.showAnswer, current.card.id) {
                if (!state.swipeEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset >= swipeThreshold && !state.showAnswer -> {
                                tickHaptics()
                                viewModel.reveal()
                            }
                            dragOffset <= -swipeThreshold && state.showAnswer -> {
                                tickHaptics()
                                viewModel.rate(Rating.AGAIN)
                            }
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { _, amount -> dragOffset += amount },
                )
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showExitDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.review_remaining, PersianText.number(state.remaining)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = viewModel::undo,
                enabled = state.canUndo,
            ) {
                Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
            }
        }

        val total = (state.reviewed + state.remaining).coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { state.reviewed.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxBadge(box = current.card.box)
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        when (direction) {
                            Direction.FA_EN -> stringResource(R.string.review_direction_fa_en)
                            Direction.CLOZE -> stringResource(R.string.review_direction_cloze)
                            else -> stringResource(R.string.review_direction_en_fa)
                        },
                    )
                },
            )
            if (state.freePractice) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.home_free_practice)) },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            QuestionCard(
                term = word.term,
                meaning = word.primaryMeaning,
                direction = direction,
                showAnswer = state.showAnswer,
                speaking = false,
                onSpeak = viewModel::speak,
                ttsUnavailable = false,
            )

            if (direction == Direction.FA_EN && !state.showAnswer) {
                OutlinedTextField(
                    value = state.typed,
                    onValueChange = viewModel::typed,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.review_answer_hint)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Inter),
                    singleLine = true,
                )
            }

            if (state.showAnswer) {
                AnswerDetails(
                    term = word.term,
                    meaning = word.meaningFa,
                    ipa = word.ipa,
                    exampleEn = word.exampleEn,
                    exampleFa = word.exampleFa,
                    direction = direction,
                    grade = state.grade,
                    gradeAnswer = state.grade?.matchedAnswer,
                )
            }

            if (state.leechWordId != null) {
                ElevatedCard {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = stringResource(R.string.review_leech_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            TextButton(onClick = {
                                viewModel.dismissLeech()
                                onOpenWord(state.leechWordId!!)
                            }) { Text(stringResource(R.string.review_add_sentence)) }
                            TextButton(onClick = viewModel::dismissLeech) {
                                Text(stringResource(R.string.action_close))
                            }
                        }
                    }
                }
            }

            if (state.saveError) {
                Text(
                    text = stringResource(R.string.review_save_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.clockAnomaly) {
                Text(
                    text = stringResource(R.string.clock_anomaly),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (!state.showAnswer) {
            if (direction == Direction.FA_EN && state.typed.isNotBlank()) {
                Button(
                    onClick = viewModel::checkAnswer,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.review_check)) }
            }
            OutlinedButton(
                onClick = viewModel::reveal,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.review_show_answer)) }
        } else {
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            RatingRow(
                previews = state.intervalPreviews,
                suggested = state.grade?.suggestedRating,
                onRate = { rating ->
                    if (state.hapticsEnabled) {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm,
                        )
                    }
                    viewModel.rate(rating)
                },
            )
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.review_exit_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.review_exit_message,
                        PersianText.number(state.remaining),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExit()
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun QuestionCard(
    term: String,
    meaning: String,
    direction: Direction,
    showAnswer: Boolean,
    speaking: Boolean,
    onSpeak: () -> Unit,
    ttsUnavailable: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val showEnglish = direction != Direction.FA_EN
            if (showEnglish) {
                Text(
                    text = BidiText.isolateLtr(term),
                    style = EnglishWordStyle,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onSpeak, enabled = !ttsUnavailable) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.review_speak),
                        tint = if (ttsUnavailable) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    text = meaning,
                    style = PersianMeaningStyle,
                    textAlign = TextAlign.Center,
                )
            }
            if (showAnswer && direction == Direction.FA_EN) {
                Text(text = BidiText.isolateLtr(term), style = EnglishWordStyle)
            }
        }
    }
}

@Composable
private fun AnswerDetails(
    term: String,
    meaning: String,
    ipa: String?,
    exampleEn: String?,
    exampleFa: String?,
    direction: Direction,
    grade: ir.wazhebox.domain.grading.GradeResult?,
    gradeAnswer: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (grade?.verdict) {
            Verdict.CORRECT -> Text(
                stringResource(R.string.review_correct),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
            Verdict.TYPO -> Text(
                stringResource(R.string.review_typo, gradeAnswer ?: ""),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Verdict.WRONG_KEYBOARD -> Text(
                stringResource(R.string.review_keyboard_wrong),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Verdict.WRONG, Verdict.REVEALED -> Text(
                stringResource(R.string.review_wrong, meaning.split('|').first()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> Unit
        }

        Text(
            text = if (direction == Direction.FA_EN) meaning else ipa ?: "",
            style = if (direction == Direction.FA_EN) PersianMeaningStyle else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!exampleEn.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.review_example),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = BidiText.isolateLtr(exampleEn), style = MaterialTheme.typography.bodyLarge)
                if (!exampleFa.isNullOrBlank()) {
                    Text(
                        text = exampleFa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingRow(
    previews: Map<Rating, Double>,
    suggested: Rating?,
    onRate: (Rating) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Rating.entries.forEach { rating ->
                val label = when (rating) {
                    Rating.AGAIN -> stringResource(R.string.review_rating_again)
                    Rating.HARD -> stringResource(R.string.review_rating_hard)
                    Rating.GOOD -> stringResource(R.string.review_rating_good)
                    Rating.EASY -> stringResource(R.string.review_rating_easy)
                }
                val container = if (rating == suggested) {
                    androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = { onRate(rating) },
                    modifier = Modifier.weight(1f),
                    colors = container,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = label, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = intervalLabel(previews[rating] ?: 0.0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Asked after the first successful session, never on first launch (PRD §17). */
@Composable
private fun NotificationPermissionRequest() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun SessionSummary(
    reviewed: Int,
    correct: Int,
    elapsedMs: Long,
    freePractice: Boolean,
    onDone: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxxl))
        Text(
            text = stringResource(R.string.summary_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (reviewed == 0) {
            EmptyState(
                title = stringResource(R.string.review_empty_today),
                message = stringResource(R.string.home_free_practice_hint),
            )
        } else {
            Text(
                text = stringResource(R.string.summary_reviewed, PersianText.number(reviewed)),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(
                    R.string.summary_correct,
                    PersianText.number(correct) + " (" +
                        PersianText.percent(if (reviewed == 0) 0.0 else correct.toDouble() / reviewed) + ")",
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = minutesLabel(elapsedMs),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!freePractice) {
            Text(
                text = stringResource(R.string.stats_retention_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        NotificationPermissionRequest()
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.summary_back_home))
        }
        TextButton(onClick = onExit) { Text(stringResource(R.string.action_close)) }
    }
}
