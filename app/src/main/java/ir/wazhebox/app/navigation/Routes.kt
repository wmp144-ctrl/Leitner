package ir.wazhebox.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (PRD §7.2: no magic strings). */

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

/**
 * [source] 0 is the normal due queue, 1 a leech drill and 2 a mistake drill;
 * primitives keep the type-safe route simple.
 */
@Serializable
data class ReviewRoute(
    val freePractice: Boolean = false,
    val deckId: Long = 0,
    val source: Int = 0,
)

@Serializable
data class WordDetailRoute(val wordId: Long)

@Serializable
data class AddWordRoute(val wordId: Long = 0)

@Serializable
data object LibraryRoute

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

@Serializable
data object DataTransferRoute

@Serializable
data object TtsSettingsRoute

@Serializable
data object DeckSettingsRoute

@Serializable
data object TroubleRoute

@Serializable
data object AboutRoute
