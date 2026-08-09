package br.com.unhasdequecor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.unhasdequecor.ui.context.ContextChoiceScreen
import br.com.unhasdequecor.ui.context.ContextChoiceViewModel
import br.com.unhasdequecor.ui.favorites.FavoritesScreen
import br.com.unhasdequecor.ui.history.HistoryRowUi
import br.com.unhasdequecor.ui.history.HistoryScreen
import br.com.unhasdequecor.ui.home.HomeScreen
import br.com.unhasdequecor.ui.profile.ProfileScreen
import br.com.unhasdequecor.ui.result.ResultScreen
import br.com.unhasdequecor.ui.style.StyleScreen

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

    fun openResultFromHistory(entry: HistoryRowUi) {
        navController.navigate(
            Routes.resultFromHistory(
                source = entry.source,
                occasion = entry.occasion,
                mood = entry.mood,
                colorId = entry.colorId,
            ),
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
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
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenResult = ::openResultFromHistory)
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(onOpenResult = ::openResultFromHistory)
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenStyle = { navController.navigate(Routes.STYLE) })
            }
        }
    }
}
