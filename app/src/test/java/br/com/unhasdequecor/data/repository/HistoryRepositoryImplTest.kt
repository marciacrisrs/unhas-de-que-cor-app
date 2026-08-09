package br.com.unhasdequecor.data.repository

import br.com.unhasdequecor.data.local.db.dao.FavoriteDao
import br.com.unhasdequecor.data.local.db.dao.HistoryDao
import br.com.unhasdequecor.data.local.db.entity.HistoryEntity
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.time.Clock
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryRepositoryImplTest {

    private val historyDao = mockk<HistoryDao>()
    private val favoriteDao = mockk<FavoriteDao>()
    private val clock = Clock { NOW_MS }
    private val repository = HistoryRepositoryImpl(historyDao, favoriteDao, clock)

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

    private companion object {
        const val NOW_MS = 42L
        const val CREATED_AT_MS = 10L
        const val EXISTING_ID = 77L
        const val NEW_ID = 12L
    }
}
