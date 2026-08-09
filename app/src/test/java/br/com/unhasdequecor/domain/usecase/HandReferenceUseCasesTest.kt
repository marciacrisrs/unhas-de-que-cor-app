package br.com.unhasdequecor.domain.usecase

import app.cash.turbine.test
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.time.Clock
import br.com.unhasdequecor.testing.FakeHandReferenceRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HandReferenceUseCasesTest {

    private val fixedNowMs = 1_720_000_000_123L
    private val clock = Clock { fixedNowMs }
    private val repository = FakeHandReferenceRepository()

    @Test
    fun `save persists with clock timestamp`() = runTest {
        val outcome = SaveHandReferenceUseCase(repository, clock)("/tmp/source.jpg")

        assertThat(outcome).isInstanceOf(HandReferenceSaveOutcome.Saved::class.java)
        val saved = (outcome as HandReferenceSaveOutcome.Saved).reference
        assertThat(saved.capturedAtEpochMs).isEqualTo(fixedNowMs)
        assertThat(saved.source).isEqualTo(HandReferenceSource.USER)
        assertThat(repository.lastSavedPath).isEqualTo("/tmp/source.jpg")
    }

    @Test
    fun `use sample marks reference as sample with id`() = runTest {
        val outcome = UseSampleHandReferenceUseCase(repository, clock)(
            sampleId = "morena_nude",
            sampleAbsolutePath = "/tmp/sample.webp",
        )

        assertThat(outcome).isInstanceOf(HandReferenceSaveOutcome.Saved::class.java)
        val saved = (outcome as HandReferenceSaveOutcome.Saved).reference
        assertThat(saved.source).isEqualTo(HandReferenceSource.SAMPLE)
        assertThat(saved.sampleId).isEqualTo("morena_nude")
        assertThat(repository.lastSampleId).isEqualTo("morena_nude")
    }

    @Test
    fun `save forwards rejection`() = runTest {
        repository.reject(HandReferenceRejection.TOO_SMALL)

        val outcome = SaveHandReferenceUseCase(repository, clock)("/tmp/tiny.jpg")

        assertThat(outcome).isEqualTo(
            HandReferenceSaveOutcome.Rejected(HandReferenceRejection.TOO_SMALL),
        )
    }

    @Test
    fun `observe and clear hand reference`() = runTest {
        val observe = ObserveHandReferenceUseCase(repository)
        val clear = ClearHandReferenceUseCase(repository)

        observe().test {
            assertThat(awaitItem()).isNull()
            repository.emit(
                HandReference(
                    localPath = "/files/hand_reference/hand.jpg",
                    capturedAtEpochMs = fixedNowMs,
                ),
            )
            assertThat(awaitItem()?.localPath).endsWith("hand.jpg")
            clear()
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
