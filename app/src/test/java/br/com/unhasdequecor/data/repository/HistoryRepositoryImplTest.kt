package br.com.unhasdequecor.data.repository

import br.com.unhasdequecor.data.local.db.dao.FavoriteDao
import br.com.unhasdequecor.data.local.db.dao.HistoryDao
import br.com.unhasdequecor.data.local.db.entity.FavoriteEntity
import br.com.unhasdequecor.data.local.db.entity.HistoryEntity
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.time.Clock
import br.com.unhasdequecor.testing.FakeColorCatalogRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryRepositoryImplTest {

    private val historyDao = mockk<HistoryDao>()
    private val favoriteDao = mockk<FavoriteDao>()
    private val clock = Clock { NOW_MS }
    private val catalog = FakeColorCatalogRepository()
    private val repository = HistoryRepositoryImpl(historyDao, favoriteDao, clock, catalog)

    @Test
    fun `save returns existing id when idempotency key already persisted`() = runTest {
        val entitySlot = slot<HistoryEntity>()
        coEvery { favoriteDao.isFavorite("romantico_rosa") } returns false
        coEvery { historyDao.insert(capture(entitySlot)) } returns -1L
        coEvery { historyDao.findIdByIdempotencyKey("session-abc") } returns EXISTING_ID

        val id = repository.save(
            HistoryEntry(
                colorId = "romantico_rosa",
                colorName = "Rosa",
                colorHex = 1L,
                tags = listOf(NailStyle.ROMANTICO),
                source = RecommendationSource.CONTEXT,
                occasion = null,
                mood = null,
                createdAtEpochMs = CREATED_AT_MS,
                idempotencyKey = "session-abc",
            ),
        )

        assertThat(id).isEqualTo(EXISTING_ID)
        assertThat(entitySlot.captured.idempotencyKey).isEqualTo("session-abc")
        coVerify(exactly = 1) { historyDao.findIdByIdempotencyKey("session-abc") }
    }

    @Test
    fun `save inserts and returns new id`() = runTest {
        coEvery { favoriteDao.isFavorite("festa_vermelha") } returns true
        coEvery { historyDao.insert(any()) } returns NEW_ID

        val id = repository.save(
            HistoryEntry(
                colorId = "festa_vermelha",
                colorName = "Vermelho",
                colorHex = 2L,
                tags = listOf(NailStyle.ELEGANTE),
                source = RecommendationSource.FOR_ME,
                occasion = null,
                mood = null,
                createdAtEpochMs = CREATED_AT_MS,
                idempotencyKey = "session-new",
            ),
        )

        assertThat(id).isEqualTo(NEW_ID)
        coVerify(exactly = 0) { historyDao.findIdByIdempotencyKey(any()) }
    }

    @Test
    fun `findByIdempotencyKey maps persisted row and live favorite flag`() = runTest {
        coEvery { historyDao.findByIdempotencyKey("session-abc") } returns HistoryEntity(
            id = EXISTING_ID,
            colorId = "romantico_rosa",
            colorName = "Rosa",
            colorHex = 1L,
            tagsCsv = "ROMANTICO",
            source = "FOR_ME",
            occasion = null,
            mood = null,
            createdAtEpochMs = CREATED_AT_MS,
            isFavorite = false,
            idempotencyKey = "session-abc",
        )
        coEvery { favoriteDao.isFavorite("romantico_rosa") } returns true

        val found = repository.findByIdempotencyKey("session-abc")

        assertThat(found?.id).isEqualTo(EXISTING_ID)
        assertThat(found?.colorId).isEqualTo("romantico_rosa")
        assertThat(found?.isFavorite).isTrue()
        assertThat(found?.idempotencyKey).isEqualTo("session-abc")
    }

    @Test
    fun `findByIdempotencyKey returns null when missing`() = runTest {
        coEvery { historyDao.findByIdempotencyKey("missing") } returns null

        assertThat(repository.findByIdempotencyKey("missing")).isNull()
    }

    @Test
    fun `observeFavorites includes catalog color favorited without history`() = runTest {
        every { historyDao.observeForFavorites() } returns flowOf(emptyList())
        every { favoriteDao.observeAll() } returns flowOf(
            listOf(FavoriteEntity(colorId = "romantico_rosa", favoritedAtEpochMs = FAVORITED_AT_MS)),
        )

        val favorites = repository.observeFavorites().first()

        assertThat(favorites).hasSize(1)
        val entry = favorites.single()
        assertThat(entry.colorId).isEqualTo("romantico_rosa")
        assertThat(entry.colorName).isEqualTo("Rosa Romance")
        assertThat(entry.colorHex).isEqualTo(0xFFE8B4B8)
        assertThat(entry.tags).contains(NailStyle.ROMANTICO)
        assertThat(entry.isFavorite).isTrue()
        assertThat(entry.createdAtEpochMs).isEqualTo(FAVORITED_AT_MS)
        assertThat(entry.source).isEqualTo(RecommendationSource.FOR_ME)
    }

    @Test
    fun `observeFavorites keeps history metadata and skips duplicate catalog orphan`() = runTest {
        every { historyDao.observeForFavorites() } returns flowOf(
            listOf(
                HistoryEntity(
                    id = EXISTING_ID,
                    colorId = "romantico_rosa",
                    colorName = "Rosa do histórico",
                    colorHex = 1L,
                    tagsCsv = "ROMANTICO",
                    source = "CONTEXT",
                    occasion = "ENCONTRO",
                    mood = "ROMANTICA",
                    createdAtEpochMs = CREATED_AT_MS,
                    isFavorite = true,
                ),
            ),
        )
        every { favoriteDao.observeAll() } returns flowOf(
            listOf(FavoriteEntity(colorId = "romantico_rosa", favoritedAtEpochMs = FAVORITED_AT_MS)),
        )

        val favorites = repository.observeFavorites().first()

        assertThat(favorites).hasSize(1)
        assertThat(favorites.single().colorName).isEqualTo("Rosa do histórico")
        assertThat(favorites.single().source).isEqualTo(RecommendationSource.CONTEXT)
        assertThat(favorites.single().createdAtEpochMs).isEqualTo(CREATED_AT_MS)
    }

    @Test
    fun `observeFavorites skips favorite whose catalog color no longer exists`() = runTest {
        every { historyDao.observeForFavorites() } returns flowOf(emptyList())
        every { favoriteDao.observeAll() } returns flowOf(
            listOf(FavoriteEntity(colorId = "cor_removida", favoritedAtEpochMs = FAVORITED_AT_MS)),
        )

        val favorites = repository.observeFavorites().first()

        assertThat(favorites).isEmpty()
    }

    private companion object {
        const val NOW_MS = 42L
        const val CREATED_AT_MS = 10L
        const val FAVORITED_AT_MS = 99L
        const val EXISTING_ID = 77L
        const val NEW_ID = 12L
    }
}
