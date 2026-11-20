package ir.wazhebox.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.getAppWidgetState
import ir.wazhebox.app.MainActivity
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.data.settings.SettingsRepository
import ir.wazhebox.core.time.StudyClock
import kotlinx.coroutines.flow.first
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home-screen widget (PRD §16): one word, its meaning behind a tap, today's
 * count and a direct path into the session. Glance renders RemoteViews, so the
 * layout is deliberately simple and uses system fonts.
 */
class WazheBoxWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // GlanceTheme is the supported way to get colours in RemoteViews;
            // building ColorProvider from raw ints is a restricted API.
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

private val WORD_KEY = stringPreferencesKey("word")
private val MEANING_KEY = stringPreferencesKey("meaning")
private val EXAMPLE_KEY = stringPreferencesKey("example")
private val COUNT_KEY = intPreferencesKey("count")
private val REVEALED_KEY = booleanPreferencesKey("revealed")

@Composable
private fun WidgetContent() {
    val state = currentState<Preferences>()
    // The widget sends people straight into a session: a real VIEW intent with
    // the app's deep link, so the nav graph opens the review route instead of
    // the home tab (ADR-016).
    val context = LocalContext.current
    val reviewAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("wazhebox://session")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )
    val word = state[WORD_KEY] ?: "واژه‌باکس"
    val meaning = state[MEANING_KEY].orEmpty()
    val example = state[EXAMPLE_KEY].orEmpty()
    val count = state[COUNT_KEY] ?: 0
    val revealed = state[REVEALED_KEY] ?: false

    // PRD §16.2 wants three widget sizes with different content. Glance reports
    // the current cell size, so one provider switches between a compact 2×2, a
    // 4×2 card and a tall 4×4 card instead of shipping three separate widgets.
    val size = LocalSize.current
    val compact = size.width < 190.dp || size.height < 130.dp
    val tall = size.height >= 200.dp

    val palette = GlanceTheme.colors
    val background = palette.surface
    val primary = palette.primary
    val muted = palette.onSurfaceVariant
    val ink = palette.onSurface

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .padding(if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = if (count > 0) "$count کارت امروز" else "امروز تمام شد ✓",
            style = TextStyle(color = primary, fontSize = 12.sp),
            maxLines = 1,
        )
        Text(
            text = word,
            style = TextStyle(
                color = ink,
                fontSize = if (compact) 16.sp else if (tall) 22.sp else 20.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = if (compact) 1 else 2,
            modifier = GlanceModifier.clickable(reviewAction),
        )

        if (!compact) {
            Spacer(GlanceModifier.height(4.dp))
            if (revealed && meaning.isNotEmpty()) {
                Text(
                    text = meaning,
                    style = TextStyle(color = muted, fontSize = 14.sp),
                    maxLines = if (tall) 3 else 2,
                )
            } else {
                Text(
                    text = "برای دیدن معنی ضربه بزن",
                    style = TextStyle(color = muted, fontSize = 12.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<RevealAction>()),
                )
            }
        }

        if (tall && revealed && example.isNotEmpty()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(text = example, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 3)
        }

        if (!compact) {
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "مرور",
                    style = TextStyle(color = primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.clickable(reviewAction),
                )
                Spacer(GlanceModifier.width(16.dp))
                Text(
                    text = "کلمهٔ بعدی",
                    style = TextStyle(color = muted, fontSize = 13.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<NextWordAction>()),
                )
            }
        }
    }
}

class RevealAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
            prefs[REVEALED_KEY] = !(prefs[REVEALED_KEY] ?: false)
        }
        WazheBoxWidget().update(context, glanceId)
    }
}

class NextWordAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        // "Next word" used to only hide the answer, so the same word stayed on
        // screen. Read what is shown, then advance the widget one step.
        val current = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[WORD_KEY]
        val updater = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        ).widgetUpdater()
        updater.refresh(skipTerm = current, onlyGlanceId = glanceId)
    }
}

/** Lets the widget callbacks reach the Hilt-provided updater. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
}

class WazheBoxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WazheBoxWidget()
}

/** Pushes a compact snapshot into every placed widget. */
@Singleton
class WidgetUpdater @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val database: WazheBoxDatabase,
    private val settingsRepository: SettingsRepository,
    private val clock: StudyClock,
) {
    /**
     * Pushes the soonest-due word into every placed widget. [skipTerm] makes the
     * pick advance to the following candidate in due order, which is what the
     * widget's "next word" action needs to actually cycle the deck.
     */
    suspend fun refresh(skipTerm: String? = null, onlyGlanceId: GlanceId? = null) {
        val settings = settingsRepository.settings.first()
        if (!settings.onboardingCompleted) return
        val now = clock.now()
        val rows = database.wordDao().allWithCards()
        val candidates = rows
            .filter { row -> row.cards.any { it.state == "REVIEW" || it.state == "LEARNING" } }
            .ifEmpty { rows }
            .sortedBy { row -> row.cards.minOfOrNull { it.dueAt } ?: Long.MAX_VALUE }
        if (candidates.isEmpty()) return

        val next = if (skipTerm.isNullOrBlank()) {
            candidates.first()
        } else {
            val index = candidates.indexOfFirst { it.word.term == skipTerm }
            if (index >= 0) candidates[(index + 1) % candidates.size] else candidates.first()
        }
        val due = database.cardDao().countDueNow(now.toEpochMilliseconds())

        val ids = onlyGlanceId?.let { listOf(it) }
            ?: androidx.glance.appwidget.GlanceAppWidgetManager(context)
                .getGlanceIds(WazheBoxWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs: MutablePreferences ->
                prefs[WORD_KEY] = next.word.term
                prefs[MEANING_KEY] = next.word.meaningFa
                prefs[EXAMPLE_KEY] = next.word.exampleEn.orEmpty()
                prefs[COUNT_KEY] = due
                prefs[REVEALED_KEY] = false
            }
            WazheBoxWidget().update(context, id)
        }
    }
}
