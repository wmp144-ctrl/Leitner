package ir.wazhebox.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.color.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.wazhebox.R
import ir.wazhebox.app.MainActivity
import ir.wazhebox.core.text.PersianText
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.data.db.WazheBoxDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home-screen widget (PRD §16): a short list of "words of the day", each with
 * its Persian translation.
 *
 * The content is computed from the database on every frame — never read back
 * from widget state that a previous frame was supposed to have written. State
 * is only used for one thing: remembering that the user tapped "تازه" today.
 *
 * Rotation:
 * - automatic: the page is derived from the current date, so a new day starts a
 *   new set of words (no background write is needed for correctness);
 * - manual: "تازه" stores a per-widget override for today and redraws.
 */
class WazheBoxWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Anything that escapes here leaves the launcher showing an empty frame,
        // so a failure is caught and rendered as a small "open the app" card.
        val page = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            ).widgetUpdater().page(id)
        }.getOrElse { error ->
            Log.w(TAG, "Could not build the widget page", error)
            WidgetPage(failed = true)
        }
        provideContent {
            // The app is light-only, so the widget pins the same light scheme
            // even when the phone is in dark mode; otherwise a white card would
            // end up with white text.
            GlanceTheme(colors = WidgetPalette) {
                WidgetCard(page)
            }
        }
    }

    companion object {
        private const val TAG = "WazheBoxWidget"
    }
}

private val WidgetScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDEBFF),
    onPrimaryContainer = Color(0xFF3A2FB0),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B27),
    surfaceVariant = Color(0xFFF4F2FC),
    onSurfaceVariant = Color(0xFF5E5B75),
    outline = Color(0xFFECE9F8),
)

private val WidgetPalette = androidx.glance.material3.ColorProviders(
    light = WidgetScheme,
    dark = WidgetScheme,
)

private val OVERRIDE_DAY_KEY = intPreferencesKey("manual_page_day")
private val OVERRIDE_PAGE_KEY = intPreferencesKey("manual_page")

/** A single entry of the card; terms are English, meanings Persian. */
data class WidgetWord(val term: String, val meaning: String)

/** Everything the card needs for one frame. */
data class WidgetPage(
    val words: List<WidgetWord> = emptyList(),
    val dueCount: Int = 0,
    val page: Int = 1,
    val pageCount: Int = 1,
    val failed: Boolean = false,
)

@Composable
private fun WidgetCard(page: WidgetPage) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val size = LocalSize.current

    // A 4×2 cell fits the header and two or three rows; a taller one shows the
    // whole page of five.
    val maxWords = when {
        size.height >= 250.dp -> WidgetPaging.PAGE_SIZE
        size.height >= 205.dp -> 4
        size.height >= 150.dp -> 3
        else -> 2
    }
    val showTitle = size.width >= 190.dp
    val showFooter = size.height >= 190.dp

    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )
    val openSession = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("wazhebox://session")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        WidgetHeader(colors = colors, showTitle = showTitle, openApp = openApp)

        when {
            page.failed -> HintBlock(
                colors = colors,
                title = context.getString(R.string.widget_error_title),
                body = context.getString(R.string.widget_error_body),
                onOpen = openApp,
            )

            page.words.isEmpty() -> HintBlock(
                colors = colors,
                title = context.getString(R.string.widget_empty_title),
                body = context.getString(R.string.widget_empty_body),
                onOpen = openApp,
            )

            else -> {
                val shown = page.words.take(maxWords)
                Spacer(GlanceModifier.height(6.dp))
                shown.forEachIndexed { index, word ->
                    if (index > 0) {
                        Spacer(GlanceModifier.height(5.dp))
                        Spacer(
                            GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.outline),
                        )
                        Spacer(GlanceModifier.height(5.dp))
                    }
                    WordRow(colors = colors, word = word, onOpen = openSession)
                }
            }
        }

        if (showFooter && page.words.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            WidgetFooter(colors = colors, page = page, onOpen = openSession)
        }
    }
}

@Composable
private fun WidgetHeader(colors: ColorProviders, showTitle: Boolean, openApp: Action) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTitle) {
            Text(
                text = context.getString(R.string.widget_title),
                style = TextStyle(
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                ),
                maxLines = 1,
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(openApp),
            )
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        Text(
            text = context.getString(R.string.widget_next_words),
            style = TextStyle(
                color = colors.onPrimaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            modifier = GlanceModifier
                .background(colors.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .clickable(actionRunCallback<NextWordsAction>()),
        )
    }
}

@Composable
private fun WordRow(colors: ColorProviders, word: WidgetWord, onOpen: Action) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // In an RTL layout the term sits on the right and the translation on
        // the left, which is how a Persian reader scans the pair.
        Text(
            text = word.term,
            style = TextStyle(
                color = colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = word.meaning,
            style = TextStyle(
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun WidgetFooter(colors: ColorProviders, page: WidgetPage, onOpen: Action) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (page.dueCount > 0) {
                context.getString(
                    R.string.widget_review_count,
                    PersianText.toPersianDigits(page.dueCount.toString()),
                )
            } else {
                context.getString(R.string.widget_no_reviews)
            },
            style = TextStyle(
                color = colors.onPrimaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            modifier = GlanceModifier
                .background(colors.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .clickable(onOpen),
        )
        Spacer(GlanceModifier.defaultWeight())
        if (page.pageCount > 1) {
            Text(
                text = context.getString(
                    R.string.widget_page,
                    PersianText.toPersianDigits(page.page.toString()),
                    PersianText.toPersianDigits(page.pageCount.toString()),
                ),
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HintBlock(colors: ColorProviders, title: String, body: String, onOpen: Action) {
    Spacer(GlanceModifier.height(8.dp))
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(onOpen),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = body,
            style = TextStyle(
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Start,
            ),
            maxLines = 3,
        )
    }
}

/** Manual change: moves this widget's card on to the next page of words. */
class NextWordsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            ).widgetUpdater().advance(glanceId)
        }.onFailure { Log.w("WazheBoxWidget", "Could not advance the widget page", it) }
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

/**
 * Builds the widget page and redraws the placed widgets. Reads only; the one
 * write is the manual "تازه" override.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: WazheBoxDatabase,
    private val clock: StudyClock,
) {
    /** The page this widget should show right now. */
    suspend fun page(id: GlanceId): WidgetPage = withContext(Dispatchers.IO) {
        val words = loadWords()
        val pageCount = WidgetPaging.pageCount(words.size)
        val today = todayEpochDay()
        val natural = WidgetPaging.pageForDay(today, pageCount)
        val override = runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        }.getOrNull()
        val current = WidgetPaging.resolvePage(
            natural = natural,
            pageCount = pageCount,
            overrideDay = override?.get(OVERRIDE_DAY_KEY),
            overridePage = override?.get(OVERRIDE_PAGE_KEY),
            today = today,
        )
        WidgetPage(
            words = WidgetPaging.slice(words, current),
            dueCount = database.cardDao().countDueNow(clock.now().toEpochMilliseconds()),
            page = current + 1,
            pageCount = pageCount,
        )
    }

    /** Redraws the widget(s) so they pick up new words, a new day or new counts. */
    suspend fun refresh(onlyGlanceId: GlanceId? = null) {
        glanceIds(onlyGlanceId).forEach { id ->
            runCatching { WazheBoxWidget().update(context, id) }
                .onFailure { Log.w("WazheBoxWidget", "Could not redraw widget $id", it) }
        }
    }

    /** Manual change: the next page, kept for the rest of the day. */
    suspend fun advance(onlyGlanceId: GlanceId? = null) {
        val today = todayEpochDay()
        val pageCount = withContext(Dispatchers.IO) { WidgetPaging.pageCount(loadWords().size) }
        val natural = WidgetPaging.pageForDay(today, pageCount)
        glanceIds(onlyGlanceId).forEach { id ->
            runCatching {
                val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                val current = WidgetPaging.resolvePage(
                    natural = natural,
                    pageCount = pageCount,
                    overrideDay = state[OVERRIDE_DAY_KEY],
                    overridePage = state[OVERRIDE_PAGE_KEY],
                    today = today,
                )
                updateAppWidgetState(context, id) { prefs: MutablePreferences ->
                    prefs[OVERRIDE_DAY_KEY] = today
                    prefs[OVERRIDE_PAGE_KEY] = (current + 1) % pageCount
                }
            }.onFailure { Log.w("WazheBoxWidget", "Could not store the next page", it) }
        }
        refresh(onlyGlanceId)
    }

    private suspend fun glanceIds(onlyGlanceId: GlanceId?): List<GlanceId> =
        onlyGlanceId?.let { listOf(it) }
            ?: runCatching {
                GlanceAppWidgetManager(context).getGlanceIds(WazheBoxWidget::class.java)
            }.getOrDefault(emptyList())

    /**
     * Deck order, which is the order the user imported the words in: consecutive
     * days then walk through the whole deck instead of reshuffling it.
     */
    private suspend fun loadWords(): List<WidgetWord> =
        database.wordDao().allWithCards()
            .asSequence()
            .filter { !it.word.suspended }
            .filter { it.word.term.isNotBlank() && it.word.meaningFa.isNotBlank() }
            .sortedBy { it.word.id }
            .map {
                WidgetWord(
                    term = it.word.term.trim(),
                    meaning = it.word.meaningFa.replace('\n', ' ').replace(WHITESPACE, " ").trim(),
                )
            }
            .toList()

    private fun todayEpochDay(): Int = LocalDate.now().toEpochDay().toInt()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * The paging rules of the widget, kept free of Android so they can be tested:
 * a page holds [PAGE_SIZE] words, the date picks the page, and a manual choice
 * wins only for the day it was made.
 */
internal object WidgetPaging {

    const val PAGE_SIZE = 5

    fun pageCount(wordCount: Int): Int =
        if (wordCount <= 0) 1 else (wordCount + PAGE_SIZE - 1) / PAGE_SIZE

    /** Automatic change: every new day moves on by one page. */
    fun pageForDay(epochDay: Int, pageCount: Int): Int =
        if (pageCount <= 1) 0 else Math.floorMod(epochDay, pageCount)

    fun resolvePage(
        natural: Int,
        pageCount: Int,
        overrideDay: Int?,
        overridePage: Int?,
        today: Int,
    ): Int = if (overrideDay == today && overridePage != null) {
        overridePage.coerceIn(0, pageCount - 1)
    } else {
        natural.coerceIn(0, pageCount - 1)
    }

    fun slice(words: List<WidgetWord>, page: Int): List<WidgetWord> {
        if (words.isEmpty()) return emptyList()
        val size = minOf(PAGE_SIZE, words.size)
        val start = (page * PAGE_SIZE) % words.size
        return List(size) { offset -> words[(start + offset) % words.size] }
    }
}
