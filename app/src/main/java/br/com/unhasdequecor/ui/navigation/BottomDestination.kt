package br.com.unhasdequecor.ui.navigation

data class BottomDestination(
    val route: String,
    val label: String,
    val contentDescription: String,
)

val mainDestinations = listOf(
    BottomDestination(Routes.HOME, "Início", "Ir para início"),
    BottomDestination(Routes.HISTORY, "Histórico", "Ir para histórico"),
    BottomDestination(Routes.CONTEXT, "Escolher", "Escolher minha cor"),
    BottomDestination(Routes.FAVORITES, "Favoritos", "Ir para favoritos"),
    BottomDestination(Routes.PROFILE, "Perfil", "Ir para perfil"),
)
