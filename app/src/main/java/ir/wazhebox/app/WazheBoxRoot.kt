package ir.wazhebox.app

import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import ir.wazhebox.R
import ir.wazhebox.app.navigation.AboutRoute
import ir.wazhebox.app.navigation.AddWordRoute
import ir.wazhebox.app.navigation.DataTransferRoute
import ir.wazhebox.app.navigation.DeckSettingsRoute
import ir.wazhebox.app.navigation.HomeRoute
import ir.wazhebox.app.navigation.LibraryRoute
import ir.wazhebox.app.navigation.OnboardingRoute
import ir.wazhebox.app.navigation.ReviewRoute
import ir.wazhebox.app.navigation.SettingsRoute
import ir.wazhebox.app.navigation.StatsRoute
import ir.wazhebox.app.navigation.TroubleRoute
import ir.wazhebox.app.navigation.TtsSettingsRoute
import ir.wazhebox.app.navigation.WordDetailRoute
import ir.wazhebox.core.ui.components.DrawerItem
import ir.wazhebox.core.ui.components.LoadingBox
import ir.wazhebox.core.ui.components.WazheBoxDrawer
import ir.wazhebox.feature.add.AddWordScreen
import ir.wazhebox.feature.decks.DeckListScreen
import ir.wazhebox.feature.detail.WordDetailScreen
import ir.wazhebox.feature.home.HomeScreen
import ir.wazhebox.feature.library.LibraryScreen
import ir.wazhebox.feature.onboarding.OnboardingScreen
import ir.wazhebox.feature.review.ReviewScreen
import ir.wazhebox.feature.trouble.TroubleScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Root composable: one NavHost plus the slide-out menu on the right.
 * The drawer replaces the bottom bar (v2), so screens keep their full height.
 * The review screen hides all chrome (focus mode, PRD §18.1).
 */
@Composable
fun WazheBoxRoot(
    // Intents that arrive after launch (widget taps, notification taps).
    newIntents: Flow<Intent>? = null,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val started = onboardingCompleted != null
    val startDestination: Any = if (onboardingCompleted == true) HomeRoute else OnboardingRoute
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (!started) {
        LoadingBox()
        return
    }

    val focusMode = destination?.isFocusMode() == true
    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }

    /** Switch tabs by replacing the back stack, the way the bottom bar did. */
    fun selectTab(route: Any) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openScreen(route: Any) {
        scope.launch { drawerState.close() }
        navController.navigate(route)
    }

    if (newIntents != null) {
        LaunchedEffect(navController) {
            newIntents.collect { intent -> navController.handleDeepLink(intent) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !focusMode,
        drawerContent = {
            WazheBoxDrawer(
                items = listOf(
                    DrawerItem(
                        label = stringResource(R.string.nav_home),
                        icon = Icons.Filled.Home,
                        selected = destination?.hasRoute<HomeRoute>() == true,
                        onClick = { selectTab(HomeRoute) },
                    ),
                    DrawerItem(
                        label = stringResource(R.string.nav_library),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        selected = destination?.hasRoute<LibraryRoute>() == true,
                        onClick = { selectTab(LibraryRoute) },
                    ),
                    DrawerItem(
                        label = stringResource(R.string.nav_decks),
                        icon = Icons.Filled.FolderOpen,
                        selected = destination?.hasRoute<DeckSettingsRoute>() == true,
                        onClick = { selectTab(DeckSettingsRoute) },
                    ),
                    DrawerItem(
                        label = stringResource(R.string.nav_stats),
                        icon = Icons.Filled.BarChart,
                        selected = destination?.hasRoute<StatsRoute>() == true,
                        onClick = { selectTab(StatsRoute) },
                    ),
                    DrawerItem(
                        label = stringResource(R.string.nav_settings),
                        icon = Icons.Filled.Settings,
                        selected = destination?.hasRoute<SettingsRoute>() == true,
                        onClick = { selectTab(SettingsRoute) },
                    ),
                ),
                actions = listOf(
                    DrawerItem(
                        label = stringResource(R.string.action_add_word),
                        icon = Icons.Filled.Add,
                        onClick = { openScreen(AddWordRoute()) },
                    ),
                    DrawerItem(
                        label = stringResource(R.string.nav_trouble),
                        icon = Icons.Filled.Warning,
                        onClick = { openScreen(TroubleRoute) },
                    ),
                    DrawerItem(
                        label = stringResource(R.string.settings_data),
                        icon = Icons.Filled.SwapVert,
                        onClick = { openScreen(DataTransferRoute) },
                    ),
                    DrawerItem(
                        label = stringResource(R.string.settings_about),
                        icon = Icons.Filled.Info,
                        onClick = { openScreen(AboutRoute) },
                    ),
                ),
            )
        },
    ) {
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    // A soft fade-and-settle between screens keeps navigation calm
                    // instead of snapping (PRD §18.7 asks for restrained motion).
                    enterTransition = {
                        fadeIn(tween(durationMillis = 220)) +
                            scaleIn(initialScale = 0.97f, animationSpec = tween(durationMillis = 260))
                    },
                    exitTransition = { fadeOut(tween(durationMillis = 140)) },
                    popEnterTransition = { fadeIn(tween(durationMillis = 220)) },
                    popExitTransition = {
                        fadeOut(tween(durationMillis = 140)) +
                            scaleOut(targetScale = 0.97f, animationSpec = tween(durationMillis = 200))
                    },
                ) {
                    composable<OnboardingRoute> {
                        OnboardingScreen(
                            onFinished = {
                                navController.navigate(HomeRoute) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable<HomeRoute> {
                        HomeScreen(
                            onStartReview = { navController.navigate(ReviewRoute()) },
                            onFreePractice = { navController.navigate(ReviewRoute(freePractice = true)) },
                            onOpenMenu = openMenu,
                            onOpenData = { navController.navigate(DataTransferRoute) },
                        )
                    }

                    composable<DeckSettingsRoute> {
                        DeckListScreen(
                            onBack = { navController.popBackStack() },
                            onReviewDeck = { deckId ->
                                navController.navigate(ReviewRoute(deckId = deckId))
                            },
                        )
                    }

                    composable<LibraryRoute> {
                        LibraryScreen(
                            onOpenWord = { wordId ->
                                navController.navigate(WordDetailRoute(wordId))
                            },
                            onOpenMenu = openMenu,
                        )
                    }

                    composable<StatsRoute> {
                        ir.wazhebox.feature.stats.StatsScreen(onOpenMenu = openMenu)
                    }
                    composable<SettingsRoute> {
                        ir.wazhebox.feature.settings.SettingsScreen(
                            onOpenMenu = openMenu,
                            onOpenDataTransfer = { navController.navigate(DataTransferRoute) },
                            onOpenTts = { navController.navigate(TtsSettingsRoute) },
                            onOpenAbout = { navController.navigate(AboutRoute) },
                        )
                    }
                    composable<TroubleRoute> {
                        TroubleScreen(
                            onBack = { navController.popBackStack() },
                            onPractice = { source ->
                                navController.navigate(ReviewRoute(source = source))
                            },
                            onOpenWord = { wordId ->
                                navController.navigate(WordDetailRoute(wordId))
                            },
                        )
                    }
                    composable<DataTransferRoute> {
                        ir.wazhebox.feature.transfer.DataTransferScreen(onBack = { navController.popBackStack() })
                    }
                    composable<TtsSettingsRoute> {
                        ir.wazhebox.feature.settings.TtsSettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable<AboutRoute> {
                        ir.wazhebox.feature.settings.AboutScreen(onBack = { navController.popBackStack() })
                    }

                    composable<WordDetailRoute>(
                        deepLinks = listOf(navDeepLink<WordDetailRoute>(basePath = "wazhebox://review")),
                    ) {
                        WordDetailScreen(
                            onBack = { navController.popBackStack() },
                            onEdit = { wordId ->
                                navController.navigate(AddWordRoute(wordId))
                            },
                        )
                    }

                    composable<AddWordRoute> {
                        AddWordScreen(
                            onDone = { navController.popBackStack() },
                            onOpenExisting = { wordId ->
                                navController.navigate(WordDetailRoute(wordId))
                            },
                        )
                    }

                    composable<ReviewRoute>(
                        deepLinks = listOf(navDeepLink<ReviewRoute>(basePath = "wazhebox://session")),
                    ) {
                        ReviewScreen(
                            onExit = { navController.popBackStack() },
                            onFinished = {
                                navController.navigate(HomeRoute) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            },
                            onOpenWord = { wordId ->
                                navController.navigate(WordDetailRoute(wordId))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun NavDestination.isFocusMode(): Boolean =
    hasRoute<ReviewRoute>() || hasRoute<AddWordRoute>() || hasRoute<OnboardingRoute>()
