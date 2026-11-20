package ir.wazhebox.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
import ir.wazhebox.core.ui.components.TapFlipCard
import ir.wazhebox.core.ui.components.LoadingBox
import ir.wazhebox.core.ui.intervalLabel
import ir.wazhebox.core.ui.minutesLabel
import ir.wazhebox.core.ui.theme.EnglishWordStyle
import ir.wazhebox.core.ui.theme.Inter
import ir.wazhebox.core.ui.theme.PersianMeaningStyle
import ir.wazhebox.core.ui.theme.Spacing
import ir.wazhebox.core.ui.theme.accents
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
        val progress by animateFloatAsState(
            targetValue = state.reviewed.toFloat() / total,
            animationSpec = tween(durationMillis = 320),
            label = "session-progress",
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
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
            // Each new card rises into place, so a review feels like a hand of
            // cards rather than a screen that swaps its text.
            val entrance = remember(current.card.id) { Animatable(0f) }
            LaunchedEffect(current.card.id) { entrance.animateTo(1f, tween(durationMillis = 280)) }

            Box(modifier = Modifier.fillMaxWidth()) {
                // Two faint layers under the card suggest the rest of the deck.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 14.dp, start = 12.dp, end = 12.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 7.dp, start = 6.dp, end = 6.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                )
                // Keying by card id gives every card a fresh flip: the next word
                // starts face up instead of unwinding the previous 180° turn.
                key(current.card.id) {
                    TapFlipCard(
                        flipped = state.showAnswer,
                        enabled = !state.showAnswer,
                        onFlip = {
                            if (!state.showAnswer) {
                                tickHaptics()
                                viewModel.reveal()
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            alpha = entrance.value
                            translationY = (1f - entrance.value) * 48f
                        },
                        front = {
                            QuestionFace(
                                term = word.term,
                                meaning = word.primaryMeaning,
                                direction = direction,
                                onSpeak = viewModel::speak,
                            )
                        },
                        back = {
                            AnswerFace(
                                term = word.term,
                                meaning = word.meaningFa,
                                ipa = word.ipa,
                                exampleEn = word.exampleEn,
                                exampleFa = word.exampleFa,
                                direction = direction,
                                grade = state.grade,
                                gradeAnswer = state.grade?.matchedAnswer,
                            )
                        },
                    )
                }
            }

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

        if (direction == Direction.FA_EN && !state.showAnswer && state.typed.isNotBlank()) {
            Button(
                onClick = viewModel::checkAnswer,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.review_check)) }
        }

        AnimatedVisibility(
            visible = state.showAnswer,
            enter = fadeIn(tween(durationMillis = 220)) +
                slideInVertically(tween(durationMillis = 260)) { height -> height / 4 },
            exit = fadeOut(tween(durationMillis = 120)),
        ) {
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

/** Front of the card: only the question, plus a hint that the card flips. */
@Composable
private fun QuestionFace(
    term: String,
    meaning: String,
    direction: Direction,
    onSpeak: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        ) {
            if (direction != Direction.FA_EN) {
                Text(
                    text = BidiText.isolateLtr(term),
                    style = EnglishWordStyle,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onSpeak) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.review_speak),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.review_tap_answer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = meaning,
                    style = PersianMeaningStyle,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.review_tap_word),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Reverse of the card: the answer, its pronunciation and both examples. */
@Composable
private fun AnswerFace(
    term: String,
    meaning: String,
    ipa: String?,
    exampleEn: String?,
    exampleFa: String?,
    direction: Direction,
    grade: ir.wazhebox.domain.grading.GradeResult?,
    gradeAnswer: String?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (direction == Direction.FA_EN) {
                Text(text = BidiText.isolateLtr(term), style = EnglishWordStyle)
            } else {
                Text(text = meaning, style = PersianMeaningStyle)
            }
            AnswerDetails(
                term = term,
                meaning = meaning,
                ipa = ipa,
                exampleEn = exampleEn,
                exampleFa = exampleFa,
                direction = direction,
                grade = grade,
                gradeAnswer = gradeAnswer,
            )
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
        Text(
            text = stringResource(R.string.review_rate_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                val tint = when (rating) {
                    Rating.AGAIN -> MaterialTheme.colorScheme.error
                    Rating.HARD -> MaterialTheme.accents.warm
                    Rating.GOOD -> MaterialTheme.colorScheme.primary
                    Rating.EASY -> MaterialTheme.accents.cool
                }
                val icon = when (rating) {
                    Rating.AGAIN -> Icons.Filled.Close
                    Rating.HARD -> Icons.Filled.TrendingDown
                    Rating.GOOD -> Icons.Filled.Check
                    Rating.EASY -> Icons.Filled.Bolt
                }
                val highlighted = rating == suggested
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(tint.copy(alpha = if (highlighted) 0.22f else 0.08f))
                        .border(
                            width = 1.dp,
                            color = tint.copy(alpha = if (highlighted) 0.55f else 0.16f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable { onRate(rating) }
                        .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
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
