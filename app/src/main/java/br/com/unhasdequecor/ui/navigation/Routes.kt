package br.com.unhasdequecor.ui.navigation

object Routes {
    const val HOME = "home"
    const val CONTEXT = "context"
    const val STYLE = "style"
    const val RESULT = "result/{source}"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"

    fun result(source: String) = "result/$source"
}

object ResultSources {
    const val CONTEXT = "context"
    const val FOR_ME = "for_me"
}
