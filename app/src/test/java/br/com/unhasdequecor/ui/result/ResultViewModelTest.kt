package br.com.unhasdequecor.ui.result

import androidx.lifecycle.SavedStateHandle
import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.usecase.GenerateAndSaveRecommendationUseCase
import br.com.unhasdequecor.domain.usecase.GeneratedRecommendation
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.RestoreRecommendationUseCase
import br.com.unhasdequecor.domain.usecase.ToggleFavoriteUseCase
import br.com.unhasdequecor.testing.FakeHandReferenceRepository
import br.com.unhasdequecor.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val generateAndSave = mockk<GenerateAndSaveRecommendationUseCase>()
    private val restoreRecommendation = mockk<RestoreRecommendationUseCase>()
    private val toggleFavorite = mockk<ToggleFavoriteUseCase>(relaxed = true)
    private val handRepository = FakeHandReferenceRepository()
    private val observeHandReference = ObserveHandReferenceUseCase(handRepository)

    private fun viewModel(handle: SavedStateHandle) = ResultViewModel(
        savedStateHandle = handle,
        generateAndSave = generateAndSave,
        restoreRecommendation = restoreRecommendation,
        toggleFavorite = toggleFavorite,
        observeHandReference = observeHandReference,
    )

    @Test
    fun `cached color restores without generating again`() = runTest {
        val recommendation = sampleRecommendation()
        coEvery {
            restoreRecommendation(
                colorId = "romantico_rosa",
                source = RecommendationSource.CONTEXT,
                context = RecommendationContext(
                    occasion = Occasion.ENCONTRO,
                    mood = Mood.ROMANTICA,
                ),
            )
        } returns GeneratedRecommendation(recommendation, isFavorite = true)

        val viewModel = viewModel(
            SavedStateHandle(
                mapOf(
                    "source" to "context",
                    "occasion" to "ENCONTRO",
                    "mood" to "ROMANTICA",
                    "colorId" to "none",
                    "result_cached_color_id" to "romantico_rosa",
                ),
            ),
        )

        assertThat(viewModel.uiState.value.recommendation?.color?.id).isEqualTo("romantico_rosa")
        assertThat(viewModel.uiState.value.isFavorite).isTrue()
        assertThat(viewModel.uiState.value.savedToHistory).isTrue()
        coVerify(exactly = 0) { generateAndSave(any(), any(), any()) }
    }

    @Test
    fun `fresh generation persists idempotency key and color cache`() = runTest {
        val recommendation = sampleRecommendation(source = RecommendationSource.FOR_ME)
        coEvery {
            generateAndSave(
                source = RecommendationSource.FOR_ME,
                context = RecommendationContext(),
                idempotencyKey = any(),
            )
        } returns GeneratedRecommendation(recommendation, isFavorite = false)

        val handle = SavedStateHandle(
            mapOf(
                "source" to "for_me",
                "occasion" to "none",
                "mood" to "none",
                "colorId" to "none",
            ),
        )
        val viewModel = viewModel(handle)

        assertThat(viewModel.uiState.value.recommendation?.color?.id).isEqualTo("romantico_rosa")
        assertThat(handle.get<String>("result_cached_color_id")).isEqualTo("romantico_rosa")
        assertThat(handle.get<String>("result_idempotency_key")).isNotEmpty()
        coVerify(exactly = 1) {
            generateAndSave(
                source = RecommendationSource.FOR_ME,
                context = RecommendationContext(),
                idempotencyKey = any(),
            )
        }
    }

    @Test
    fun `observes hand reference for try-on preview`() = runTest {
        coEvery {
            generateAndSave(any(), any(), any())
        } returns GeneratedRecommendation(
            sampleRecommendation(source = RecommendationSource.FOR_ME),
            isFavorite = false,
        )
        val viewModel = viewModel(
            SavedStateHandle(
                mapOf(
                    "source" to "for_me",
                    "occasion" to "none",
                    "mood" to "none",
                    "colorId" to "none",
                ),
            ),
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.hasHandReference).isFalse()

        val capturedAt = 1_700_000_000_000L
        handRepository.emit(
            HandReference(
                localPath = "/files/hand_reference/hand_1.jpg",
                capturedAtEpochMs = capturedAt,
                source = HandReferenceSource.SAMPLE,
                sampleId = "media_rosa",
            ),
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasHandReference).isTrue()
        assertThat(viewModel.uiState.value.isSampleHand).isTrue()
        assertThat(viewModel.uiState.value.handSampleId).isEqualTo("media_rosa")
        assertThat(viewModel.uiState.value.handRevision).isEqualTo(capturedAt)
    }

    @Test
    fun `history colorId arg restores without generating`() = runTest {
        val recommendation = sampleRecommendation()
        coEvery {
            restoreRecommendation(
                colorId = "romantico_rosa",
                source = RecommendationSource.CONTEXT,
                context = RecommendationContext(
                    occasion = Occasion.ENCONTRO,
                    mood = Mood.ROMANTICA,
                ),
            )
        } returns GeneratedRecommendation(recommendation, isFavorite = false)

        val handle = SavedStateHandle(
            mapOf(
                "source" to "context",
                "occasion" to "ENCONTRO",
                "mood" to "ROMANTICA",
                "colorId" to "romantico_rosa",
            ),
        )
        val viewModel = viewModel(handle)

        assertThat(viewModel.uiState.value.recommendation?.color?.id).isEqualTo("romantico_rosa")
        assertThat(handle.get<String>("result_cached_color_id")).isEqualTo("romantico_rosa")
        coVerify(exactly = 0) { generateAndSave(any(), any(), any()) }
    }

    @Test
    fun `recommendAgain clears cache and generates new session`() = runTest {
        val first = sampleRecommendation(colorId = "romantico_rosa", source = RecommendationSource.FOR_ME)
        val second = sampleRecommendation(
            colorId = "festa_vermelha",
            name = "Vermelho",
            source = RecommendationSource.FOR_ME,
        )
        coEvery {
            generateAndSave(any(), any(), any())
        } returnsMany listOf(
            GeneratedRecommendation(first, false),
            GeneratedRecommendation(second, false),
        )

        val handle = SavedStateHandle(
            mapOf(
                "source" to "for_me",
                "occasion" to "none",
                "mood" to "none",
                "colorId" to "none",
            ),
        )
        val viewModel = viewModel(handle)
        val firstKey = handle.get<String>("result_idempotency_key")

        viewModel.recommendAgain()

        assertThat(viewModel.uiState.value.recommendation?.color?.id).isEqualTo("festa_vermelha")
        assertThat(handle.get<String>("result_idempotency_key")).isNotEqualTo(firstKey)
        coVerify(exactly = 2) { generateAndSave(any(), any(), any()) }
    }

    private fun sampleRecommendation(
        colorId: String = "romantico_rosa",
        name: String = "Rosa",
        source: RecommendationSource = RecommendationSource.CONTEXT,
    ): ColorRecommendation {
        val color = NailColor(
            id = colorId,
            name = name,
            hex = 0xFFE91E63,
            tags = listOf(NailStyle.ROMANTICO),
            description = "desc",
            tip = "dica",
            occasions = setOf(Occasion.ENCONTRO),
            moods = setOf(Mood.ROMANTICA),
        )
        return ColorRecommendation(
            color = color,
            similarColors = emptyList(),
            source = source,
            context = RecommendationContext(
                occasion = Occasion.ENCONTRO.takeIf { source == RecommendationSource.CONTEXT },
                mood = Mood.ROMANTICA.takeIf { source == RecommendationSource.CONTEXT },
            ),
            rationale = "porque sim",
        )
    }
}
