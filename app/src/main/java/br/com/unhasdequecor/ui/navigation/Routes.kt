package br.com.unhasdequecor.ui.navigation

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationSource

object Routes {
    const val HOME = "home"
    const val CONTEXT = "context"
    const val STYLE = "style"
    const val RESULT = "result/{source}/{occasion}/{mood}"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"

    private const val NONE = "none"

    fun resultForMe(): String = "result/${ResultSources.FOR_ME}/$NONE/$NONE"

    fun resultByContext(occasion: Occasion, mood: Mood): String =
        "result/${ResultSources.CONTEXT}/${occasion.name}/${mood.name}"

    fun parseOccasion(raw: String): Occasion? =
        raw.takeUnless { it == NONE }?.let { runCatching { Occasion.valueOf(it) }.getOrNull() }

    fun parseMood(raw: String): Mood? =
        raw.takeUnless { it == NONE }?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
}

object ResultSources {
    const val CONTEXT = "context"
    const val FOR_ME = "for_me"

    fun toDomain(source: String): RecommendationSource = when (source) {
        FOR_ME -> RecommendationSource.FOR_ME
        else -> RecommendationSource.CONTEXT
    }
}
