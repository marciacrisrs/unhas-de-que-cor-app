package br.com.unhasdequecor.domain.repository

import br.com.unhasdequecor.domain.model.NailColor

/**
 * Fonte de leitura do catálogo estático de cores de esmalte usado nas recomendações.
 */
interface ColorCatalogRepository {
    fun getAll(): List<NailColor>
    fun getById(id: String): NailColor?
}
