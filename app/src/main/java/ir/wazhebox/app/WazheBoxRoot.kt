package ir.wazhebox.app

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import ir.wazhebox.R
import ir.wazhebox.app.navigation.AddWordRoute
import ir.wazhebox.app.navigation.HomeRoute
import ir.wazhebox.app.navigation.LibraryRoute
import ir.wazhebox.app.navigation.OnboardingRoute
import ir.wazhebox.app.navigation.ReviewRoute
import ir.wazhebox.app.navigation.SettingsRoute
import ir.wazhebox.app.navigation.StatsRoute
import ir.wazhebox.app.navigation.WordDetailRoute
import ir.wazhebox.core.ui.components.LoadingBox
import ir.wazhebox.feature.add.AddWordScreen
import ir.wazhebox.feature.detail.WordDetailScreen
import ir.wazhebox.feature.home.HomeScreen
import ir.wazhebox.feature.library.LibraryScreen
import ir.wazhebox.feature.onboarding.OnboardingScreen
import ir.wazhebox.feature.review.ReviewScreen

/**
 * Root composable: one NavHost plus the bottom bar.
 * The review screen hides all chrome (focus mode, PRD §18.1).
 */
@Composable
fun WazheBoxRoot(
    viewModel: RootViewModel = hiltViewModel(),
) {
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val started = onboardingCompleted != null
    val startDestination: Any = if (onboardingCompleted == true) HomeRoute else OnboardingRoute

    if (!started) {
        LoadingBox()
        return
    }

    val showBottomBar = destination == null || !destination.isFocusMode()
    val showFab = destination?.let {
        it.hasRoute<HomeRoute>() || it.hasRoute<LibraryRoute>()
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { navController.navigate(AddWordRoute()) },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_word))
                }
            }
        },
    ) { innerPadding ->
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
                        onAddWord = { navController.navigate(AddWordRoute()) },
                    )
                }

                composable<LibraryRoute> {
                    LibraryScreen(onOpenWord = { wordId ->
                        navController.navigate(WordDetailRoute(wordId))
                    })
                }

                composable<StatsRoute> { ir.wazhebox.feature.stats.StatsScreen() }
                composable<SettingsRoute> {
                    ir.wazhebox.feature.settings.SettingsScreen(
                        onOpenDataTransfer = { navController.navigate(ir.wazhebox.app.navigation.DataTransferRoute) },
                        onOpenTts = { navController.navigate(ir.wazhebox.app.navigation.TtsSettingsRoute) },
                        onOpenAbout = { navController.navigate(ir.wazhebox.app.navigation.AboutRoute) },
                    )
                }
                composable<ir.wazhebox.app.navigation.DataTransferRoute> {
                    ir.wazhebox.feature.transfer.DataTransferScreen(onBack = { navController.popBackStack() })
                }
                composable<ir.wazhebox.app.navigation.TtsSettingsRoute> {
                    ir.wazhebox.feature.settings.TtsSettingsScreen(onBack = { navController.popBackStack() })
                }
                composable<ir.wazhebox.app.navigation.AboutRoute> {
                    ir.wazhebox.feature.settings.AboutScreen(onBack = { navController.popBackStack() })
                }

                composable<WordDetailRoute>(
                    deepLinks = listOf(navDeepLink<WordDetailRoute>(basePath = "wazhebox://review")),
                ) { entry ->
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

private fun NavDestination.isFocusMode(): Boolean =
    hasRoute<ReviewRoute>() || hasRoute<AddWordRoute>() || hasRoute<OnboardingRoute>()

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(text = title, modifier = Modifier.padding(24.dp))
    }
}

private enum class BottomTab(
    val route: Any,
    val routeClass: kotlin.reflect.KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Home(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Filled.Home),
    Library(LibraryRoute, LibraryRoute::class, R.string.nav_library, Icons.AutoMirrored.Filled.MenuBook),
    Stats(StatsRoute, StatsRoute::class, R.string.nav_stats, Icons.Filled.BarChart),
    Settings(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Filled.Settings),
}
