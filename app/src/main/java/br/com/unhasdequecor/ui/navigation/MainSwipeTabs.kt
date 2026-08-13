package br.com.unhasdequecor.ui.navigation

/**
 * Abas laterais da bottom bar que trocam com swipe horizontal.
 * O FAB “Escolher” ([Routes.CONTEXT]) fica fora do pager (fluxo com stack própria).
 */
object MainSwipeTabs {
    val routes: List<String> = listOf(
        Routes.HOME,
        Routes.HISTORY,
        Routes.FAVORITES,
        Routes.PROFILE,
    )

    fun indexOf(route: String?): Int? {
        val idx = routes.indexOf(route)
        return idx.takeIf { it >= 0 }
    }

    fun routeAt(index: Int): String =
        routes.getOrElse(index) { Routes.HOME }

    fun contains(route: String?): Boolean =
        route != null && route in routes
}
