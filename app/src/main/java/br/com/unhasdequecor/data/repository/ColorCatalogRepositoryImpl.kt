package br.com.unhasdequecor.data.repository

import br.com.unhasdequecor.data.catalog.DefaultColorCatalog
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorCatalogRepositoryImpl @Inject constructor() : ColorCatalogRepository {
    override fun getAll(): List<NailColor> = DefaultColorCatalog.colors

    override fun getById(id: String): NailColor? = DefaultColorCatalog.byId(id)
}
