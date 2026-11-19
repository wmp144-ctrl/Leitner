package ir.wazhebox.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.wazhebox.R
import ir.wazhebox.app.navigation.AddWordRoute
import ir.wazhebox.app.navigation.DataTransferRoute
import ir.wazhebox.app.navigation.HomeRoute
import ir.wazhebox.app.navigation.LibraryRoute
import ir.wazhebox.app.navigation.OnboardingRoute
import ir.wazhebox.app.navigation.ReviewRoute
import ir.wazhebox.app.navigation.SessionSummaryRoute
import ir.wazhebox.app.navigation.SettingsRoute
import ir.wazhebox.app.navigation.StatsRoute
import ir.wazhebox.app.navigation.WordDetailRoute

/**
 * Root composable: one NavHost plus the bottom bar.
 * The review screen hides all chrome (focus mode, PRD §18.1).
 */
@Composable
fun WazheBoxRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination == null || !destination.isFocusMode()

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
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<OnboardingRoute> { PlaceholderScreen(stringResource(R.string.app_name)) }
            composable<HomeRoute> { PlaceholderScreen(stringResource(R.string.nav_home)) }
            composable<LibraryRoute> { PlaceholderScreen(stringResource(R.string.nav_library)) }
            composable<StatsRoute> { PlaceholderScreen(stringResource(R.string.nav_stats)) }
            composable<SettingsRoute> { PlaceholderScreen(stringResource(R.string.nav_settings)) }
            composable<AddWordRoute> { PlaceholderScreen(stringResource(R.string.word_add_title)) }
            composable<SessionSummaryRoute> { PlaceholderScreen(stringResource(R.string.summary_title)) }
            composable<DataTransferRoute> { PlaceholderScreen(stringResource(R.string.io_import_title)) }
            composable<ReviewRoute> { PlaceholderScreen(stringResource(R.string.review_show_answer)) }
            composable<WordDetailRoute> { PlaceholderScreen(stringResource(R.string.detail_title)) }
        }
    }
}

private fun androidx.navigation.NavDestination.isFocusMode(): Boolean =
    hasRoute<ReviewRoute>() || hasRoute<AddWordRoute>() || hasRoute<OnboardingRoute>()

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title)
        }
    }
}

private enum class BottomTab(
    val route: Any,
    val routeClass: kotlin.reflect.KClass<*>,
    val labelRes: Int,
    val icon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector,
) {
    Home(HomeRoute, HomeRoute::class, R.string.nav_home, { Icons.Filled.Home }),
    Library(LibraryRoute, LibraryRoute::class, R.string.nav_library, { Icons.AutoMirrored.Filled.MenuBook }),
    Stats(StatsRoute, StatsRoute::class, R.string.nav_stats, { Icons.Filled.BarChart }),
    Settings(SettingsRoute, SettingsRoute::class, R.string.nav_settings, { Icons.Filled.Settings }),
}
