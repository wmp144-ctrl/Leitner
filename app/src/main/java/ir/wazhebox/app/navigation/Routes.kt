package ir.wazhebox.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes (PRD §7.2: no magic strings). */

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data class ReviewRoute(val freePractice: Boolean = false)

@Serializable
data class WordDetailRoute(val wordId: Long)

@Serializable
data object AddWordRoute

@Serializable
data object LibraryRoute

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

@Serializable
data object SessionSummaryRoute

@Serializable
data object DataTransferRoute

@Serializable
data object TtsSettingsRoute

@Serializable
data object DeckSettingsRoute

@Serializable
data object AboutRoute
