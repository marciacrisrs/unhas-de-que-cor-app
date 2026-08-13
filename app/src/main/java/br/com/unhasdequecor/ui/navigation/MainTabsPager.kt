package br.com.unhasdequecor.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.unhasdequecor.ui.history.HistoryRowUi
import br.com.unhasdequecor.ui.history.HistoryScreen
import br.com.unhasdequecor.ui.history.HistoryScreenMode
import br.com.unhasdequecor.ui.home.HomeScreen
import br.com.unhasdequecor.ui.profile.ProfileScreen

/**
 * Pager das abas principais — deslizar troca Início / Histórico / Favoritos / Perfil.
 */
@Composable
fun MainTabsPager(
    pagerState: PagerState,
    onChooseByContext: () -> Unit,
    onChooseForMe: () -> Unit,
    onOpenStyle: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHandReference: () -> Unit,
    onOpenInspiration: (colorId: String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenResultFromHistory: (HistoryRowUi) -> Unit,
    onSwipeBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { page -> MainSwipeTabs.routeAt(page) },
    ) { page ->
        when (MainSwipeTabs.routeAt(page)) {
            Routes.HOME -> HomeScreen(
                onChooseByContext = onChooseByContext,
                onChooseForMe = onChooseForMe,
                onOpenStyle = onOpenStyle,
                onOpenHistory = onOpenHistory,
                onOpenFavorites = onOpenFavorites,
                onOpenHandReference = onOpenHandReference,
                onOpenInspiration = onOpenInspiration,
            )
            Routes.HISTORY -> HistoryScreen(
                onOpenResult = onOpenResultFromHistory,
                mode = HistoryScreenMode.FULL,
                viewModel = hiltViewModel(key = "tab_history"),
            )
            Routes.FAVORITES -> HistoryScreen(
                onOpenResult = onOpenResultFromHistory,
                mode = HistoryScreenMode.FAVORITES_ONLY,
                onBack = onSwipeBackToHome,
                viewModel = hiltViewModel(key = "tab_favorites"),
            )
            Routes.PROFILE -> ProfileScreen(
                onOpenStyle = onOpenStyle,
                onOpenHandReference = onOpenHandReference,
                onOpenHistory = onOpenHistory,
                onOpenAbout = onOpenAbout,
            )
        }
    }
}
