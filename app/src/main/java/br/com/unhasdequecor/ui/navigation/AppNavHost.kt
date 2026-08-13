package br.com.unhasdequecor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.unhasdequecor.ui.about.AboutScreen
import br.com.unhasdequecor.ui.context.ContextChoiceScreen
import br.com.unhasdequecor.ui.context.ContextChoiceViewModel
import br.com.unhasdequecor.ui.history.HistoryRowUi
import br.com.unhasdequecor.ui.history.HistoryScreen
import br.com.unhasdequecor.ui.history.HistoryScreenMode
import br.com.unhasdequecor.ui.hand.HandReferenceScreen
import br.com.unhasdequecor.ui.home.HomeScreen
import br.com.unhasdequecor.ui.home.HomeViewModel
import br.com.unhasdequecor.ui.profile.ProfileScreen
import br.com.unhasdequecor.ui.result.ResultScreen
import br.com.unhasdequecor.ui.style.StyleScreen

private fun NavController.openResultFromHistory(entry: HistoryRowUi) {
    navigate(
        Routes.resultFromHistory(
            source = entry.source,
            occasion = entry.occasion,
            mood = entry.mood,
            colorId = entry.colorId,
        ),
    )
}

private fun NavController.returnHomeAfterHandSelected(flash: String?) {
    runCatching { getBackStackEntry(Routes.HOME) }
        .getOrNull()
        ?.savedStateHandle
        ?.set(HomeViewModel.FLASH_MESSAGE_KEY, flash)
    val popped = popBackStack(Routes.HOME, inclusive = false)
    if (!popped) {
        navigate(Routes.HOME) {
            launchSingleTop = true
        }
    }
}

private fun NavController.navigateBottomTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.HOME,
        Routes.HISTORY,
        Routes.FAVORITES,
        Routes.PROFILE,
        Routes.CONTEXT,
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navController::navigateBottomTab,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onChooseByContext = { navController.navigate(Routes.CONTEXT) },
                    onChooseForMe = {
                        navController.navigate(Routes.resultForMe())
                    },
                    onOpenStyle = { navController.navigate(Routes.STYLE) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                    onOpenHandReference = { navController.navigate(Routes.HAND_REFERENCE) },
                )
            }
            composable(Routes.CONTEXT) {
                val viewModel: ContextChoiceViewModel = hiltViewModel()
                ContextChoiceScreen(
                    viewModel = viewModel,
                    onContinue = { occasion, mood ->
                        navController.navigate(Routes.resultByContext(occasion, mood))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.STYLE) {
                StyleScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HAND_REFERENCE) {
                HandReferenceScreen(
                    onBack = { navController.popBackStack() },
                    onHandSelected = navController::returnHomeAfterHandSelected,
                )
            }
            composable(
                route = Routes.RESULT,
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("occasion") { type = NavType.StringType },
                    navArgument("mood") { type = NavType.StringType },
                    navArgument("colorId") { type = NavType.StringType },
                ),
            ) {
                ResultScreen(
                    onBack = { navController.popBackStack() },
                    onOpenHistory = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onOpenHandReference = {
                        navController.navigate(Routes.HAND_REFERENCE)
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenResult = navController::openResultFromHistory)
            }
            composable(Routes.FAVORITES) {
                HistoryScreen(
                    onOpenResult = navController::openResultFromHistory,
                    mode = HistoryScreenMode.FAVORITES_ONLY,
                    onBack = { navController.navigateBottomTab(Routes.HOME) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenStyle = { navController.navigate(Routes.STYLE) },
                    onOpenHandReference = { navController.navigate(Routes.HAND_REFERENCE) },
                    onOpenHistory = { navController.navigateBottomTab(Routes.HISTORY) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
