package br.com.unhasdequecor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import br.com.unhasdequecor.ui.hand.HandReferenceScreen
import br.com.unhasdequecor.ui.home.HomeViewModel
import br.com.unhasdequecor.ui.result.ResultScreen
import br.com.unhasdequecor.ui.style.StyleScreen
import kotlinx.coroutines.launch

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
    runCatching { getBackStackEntry(Routes.MAIN) }
        .getOrNull()
        ?.savedStateHandle
        ?.set(HomeViewModel.FLASH_MESSAGE_KEY, flash)
    val popped = popBackStack(Routes.MAIN, inclusive = false)
    if (!popped) {
        navigate(Routes.MAIN) {
            launchSingleTop = true
        }
    }
}

private fun NavController.navigateToMainShell() {
    navigate(Routes.MAIN) {
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
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { MainSwipeTabs.routes.size },
    )

    val onMainShell = currentRoute == Routes.MAIN
    val bottomBarRoute = if (onMainShell) {
        MainSwipeTabs.routeAt(pagerState.currentPage)
    } else {
        currentRoute
    }
    val showBottomBar = onMainShell || currentRoute == Routes.CONTEXT

    fun goToSwipeTab(route: String) {
        if (!onMainShell) {
            navController.navigateToMainShell()
        }
        val page = MainSwipeTabs.indexOf(route) ?: return
        scope.launch {
            pagerState.animateScrollToPage(page)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = bottomBarRoute,
                    onNavigate = { route ->
                        when {
                            route == Routes.CONTEXT -> {
                                navController.navigate(Routes.CONTEXT) {
                                    launchSingleTop = true
                                }
                            }
                            MainSwipeTabs.contains(route) -> goToSwipeTab(route)
                            else -> navController.navigateToMainShell()
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.MAIN) {
                MainTabsPager(
                    pagerState = pagerState,
                    onChooseByContext = {
                        navController.navigate(Routes.CONTEXT) {
                            launchSingleTop = true
                        }
                    },
                    onChooseForMe = {
                        navController.navigate(Routes.resultForMe())
                    },
                    onOpenStyle = { navController.navigate(Routes.STYLE) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                    onOpenHandReference = { navController.navigate(Routes.HAND_REFERENCE) },
                    onOpenInspiration = { colorId ->
                        navController.navigate(Routes.resultForColor(colorId))
                    },
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
                        navController.popBackStack(Routes.MAIN, inclusive = false)
                        goToSwipeTab(Routes.HISTORY)
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
