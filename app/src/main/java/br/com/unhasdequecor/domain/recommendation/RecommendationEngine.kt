package br.com.unhasdequecor.domain.recommendation

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Motor inicial de recomendação baseado em pontuação por contexto, estilo e histórico.
 * Princípio do produto: não existe cor certa ou errada — apenas uma sugestão para o momento.
 */
@Singleton
class RecommendationEngine @Inject constructor() {

    fun recommendByContext(
        catalog: List<NailColor>,
        context: RecommendationContext,
        recentColorIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): ColorRecommendation {
        require(catalog.isNotEmpty()) { "Catálogo de cores não pode ser vazio." }

        val ranked = catalog
            .map { color -> color to score(color, context, recentColorIds) }
            .sortedByDescending { it.second }

        val topScore = ranked.first().second
        val topCandidates = ranked
            .filter { it.second >= topScore - SCORE_BAND }
            .map { it.first }
            .ifEmpty { listOf(ranked.first().first) }

        val selected = topCandidates[random.nextInt(topCandidates.size)]
        return buildRecommendation(
            selected = selected,
            catalog = catalog,
            context = context,
            source = RecommendationSource.CONTEXT,
        )
    }

    fun recommendForMe(
        catalog: List<NailColor>,
        preferredStyles: Set<br.com.unhasdequecor.domain.model.NailStyle>,
        recentColorIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): ColorRecommendation {
        require(catalog.isNotEmpty()) { "Catálogo de cores não pode ser vazio." }

        val context = RecommendationContext(preferredStyles = preferredStyles)
        val eligible = catalog.filterNot { it.id in recentColorIds }.ifEmpty { catalog }
        val preferred = if (preferredStyles.isEmpty()) {
            eligible
        } else {
            eligible.filter { color -> color.tags.any { it in preferredStyles } }
                .ifEmpty { eligible }
        }

        val selected = preferred[random.nextInt(preferred.size)]
        return buildRecommendation(
            selected = selected,
            catalog = catalog,
            context = context,
            source = RecommendationSource.FOR_ME,
        )
    }

    fun score(
        color: NailColor,
        context: RecommendationContext,
        recentColorIds: Set<String>,
    ): Int {
        var total = 0

        context.occasion?.let { occasion ->
            if (occasion in color.occasions) total += OCCASION_MATCH else total += OCCASION_MISS
        }
        context.mood?.let { mood ->
            if (mood in color.moods) total += MOOD_MATCH else total += MOOD_MISS
        }
        context.season?.let { season ->
            if (season in color.seasons) total += SEASON_MATCH else total += SEASON_MISS
        }
        if (context.preferredStyles.isNotEmpty()) {
            val overlap = color.tags.count { it in context.preferredStyles }
            total += overlap * STYLE_MATCH
        }
        if (color.id in recentColorIds) {
            total += RECENT_PENALTY
        }
        return total
    }

    private fun buildRecommendation(
        selected: NailColor,
        catalog: List<NailColor>,
        context: RecommendationContext,
        source: RecommendationSource,
    ): ColorRecommendation {
        val similar = selected.similarColorIds
            .mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            .filterNot { it.id == selected.id }
            .take(5)

        val rationale = when (source) {
            RecommendationSource.CONTEXT -> buildContextRationale(selected, context)
            RecommendationSource.FOR_ME ->
                "${selected.name} veio para te surpreender — coerente com o seu estilo e diferente das últimas escolhas."
        }

        return ColorRecommendation(
            color = selected,
            similarColors = similar,
            source = source,
            context = context,
            rationale = rationale,
        )
    }

    private fun buildContextRationale(
        color: NailColor,
        context: RecommendationContext,
    ): String {
        val parts = buildList {
            context.occasion?.let { add("ocasião ${it.displayName.lowercase()}") }
            context.mood?.let { add("mood ${it.displayName.lowercase()}") }
            if (context.preferredStyles.isNotEmpty()) {
                add("estilo ${context.preferredStyles.first().displayName.lowercase()}")
            }
        }
        val moment = if (parts.isEmpty()) {
            "o seu momento atual"
        } else {
            parts.joinToString(", ")
        }
        return "${color.description} Combina com $moment."
    }

    private companion object {
        const val OCCASION_MATCH = 40
        const val OCCASION_MISS = -5
        const val MOOD_MATCH = 30
        const val MOOD_MISS = -3
        const val SEASON_MATCH = 20
        const val SEASON_MISS = -2
        const val STYLE_MATCH = 15
        const val RECENT_PENALTY = -50
        const val SCORE_BAND = 15
    }
}
