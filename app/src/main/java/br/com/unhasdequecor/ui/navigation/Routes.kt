package br.com.unhasdequecor.ui.navigation

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationSource

object Routes {
    const val MAIN = "main"
    const val HOME = "home"
    const val CONTEXT = "context"
    const val STYLE = "style"
    const val RESULT = "result/{source}/{occasion}/{mood}/{colorId}"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"
    const val HAND_REFERENCE = "hand_reference"
    const val LIVE_TRY_ON = "live_try_on"
    const val ABOUT = "about"
    const val NONE = "none"

    fun resultForMe(): String = "result/${ResultSources.FOR_ME}/$NONE/$NONE/$NONE"
    fun resultByContext(occasion: Occasion, mood: Mood): String =
        "result/${ResultSources.CONTEXT}/${occasion.name}/${mood.name}/$NONE"
    fun resultFromHistory(
        source: RecommendationSource,
        occasion: Occasion?,
        mood: Mood?,
        colorId: String,
    ): String {
        val sourcePath = when (source) {
            RecommendationSource.FOR_ME -> ResultSources.FOR_ME
            RecommendationSource.CONTEXT -> ResultSources.CONTEXT
        }
        return "result/$sourcePath/${occasion?.name ?: NONE}/${mood?.name ?: NONE}/$colorId"
    }
    fun resultForColor(colorId: String): String =
        "result/${ResultSources.FOR_ME}/$NONE/$NONE/$colorId"
    fun parseOccasion(raw: String): Occasion? =
        raw.takeUnless { it == NONE }?.let { runCatching { Occasion.valueOf(it) }.getOrNull() }
    fun parseMood(raw: String): Mood? =
        raw.takeUnless { it == NONE }?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
    fun parseColorId(raw: String): String? = raw.takeUnless { it == NONE || it.isBlank() }
}

object ResultSources {
    const val CONTEXT = "context"
    const val FOR_ME = "for_me"
    fun toDomain(source: String): RecommendationSource = when (source) {
        FOR_ME -> RecommendationSource.FOR_ME
        else -> RecommendationSource.CONTEXT
    }
}
