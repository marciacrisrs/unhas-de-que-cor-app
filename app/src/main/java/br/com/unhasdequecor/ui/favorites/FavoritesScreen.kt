package br.com.unhasdequecor.ui.favorites

import androidx.compose.runtime.Composable
import br.com.unhasdequecor.ui.history.HistoryScreen
import br.com.unhasdequecor.ui.history.HistoryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect

@Composable
fun FavoritesScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.setFavoritesOnly(true)
    }
    HistoryScreen(viewModel = viewModel)
}
