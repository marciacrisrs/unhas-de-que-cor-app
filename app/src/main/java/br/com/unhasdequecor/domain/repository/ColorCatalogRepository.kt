package br.com.unhasdequecor.domain.repository

import br.com.unhasdequecor.domain.model.NailColor

interface ColorCatalogRepository {
    fun getAll(): List<NailColor>
    fun getById(id: String): NailColor?
}
