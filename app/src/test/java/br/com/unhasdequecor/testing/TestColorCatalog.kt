package br.com.unhasdequecor.testing

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository

object TestColorCatalog {
    val colors: List<NailColor> = listOf(
        NailColor(
            id = "festa_vermelha",
            name = "Vermelho Festa",
            hex = 0xFFC6283A,
            tags = listOf(NailStyle.ELEGANTE, NailStyle.MARCANTE),
            description = "Para brilhar",
            tip = "Combina com preto",
            occasions = setOf(Occasion.FESTA),
            moods = setOf(Mood.ENERGETICA),
            similarColorIds = listOf("dia_nude"),
        ),
        NailColor(
            id = "dia_nude",
            name = "Nude Dia",
            hex = 0xFFE2C4B8,
            tags = listOf(NailStyle.MINIMALISTA, NailStyle.NEUTRO),
            description = "Clean",
            tip = "Combina com tudo",
            occasions = setOf(Occasion.DIA_A_DIA, Occasion.TRABALHO),
            moods = setOf(Mood.NEUTRA, Mood.TRANQUILA),
            similarColorIds = listOf("festa_vermelha"),
        ),
        NailColor(
            id = "romantico_rosa",
            name = "Rosa Romance",
            hex = 0xFFE8B4B8,
            tags = listOf(NailStyle.ROMANTICO, NailStyle.DELICADO),
            description = "Delicado",
            tip = "Looks florais",
            occasions = setOf(Occasion.ENCONTRO, Occasion.DIA_A_DIA),
            moods = setOf(Mood.ROMANTICA),
            similarColorIds = listOf("dia_nude"),
        ),
    )
}

class FakeColorCatalogRepository(
    private val items: List<NailColor> = TestColorCatalog.colors,
) : ColorCatalogRepository {
    override fun getAll(): List<NailColor> = items
    override fun getById(id: String): NailColor? = items.firstOrNull { it.id == id }
}
