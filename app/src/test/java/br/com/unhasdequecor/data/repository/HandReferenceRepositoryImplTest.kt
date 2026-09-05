package br.com.unhasdequecor.data.repository

import android.content.Context
import app.cash.turbine.test
import br.com.unhasdequecor.data.local.hand.HandReferenceFileStore
import br.com.unhasdequecor.data.local.hand.HandReferencePreferencesDataSource
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.time.Clock
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HandReferenceRepositoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<HandReferencePreferencesDataSource>()
    private val fileStore = mockk<HandReferenceFileStore>()
    private val clock = Clock { FIXED_NOW_MS }
    private val repository = HandReferenceRepositoryImpl(context, preferences, fileStore, clock)

    @Test
    fun `observe emits null when stored file is missing`() = runTest {
        val stale = HandReference(
            localPath = "/files/hand_reference/hand_1.jpg",
            capturedAtEpochMs = FIXED_NOW_MS,
            source = HandReferenceSource.USER,
        )
        every { preferences.observe() } returns flowOf(stale)
        every { fileStore.fileExists(stale.localPath) } returns false

        repository.observe().test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save clears staging cache after a successful persist`() = runTest {
        val saved = HandReference(
            localPath = "/files/hand_reference/hand_2.jpg",
            capturedAtEpochMs = FIXED_NOW_MS,
            source = HandReferenceSource.USER,
        )
        every {
            fileStore.persist(
                sourceAbsolutePath = "/tmp/capture.jpg",
                capturedAtEpochMs = FIXED_NOW_MS,
                source = HandReferenceSource.USER,
                sampleId = null,
            )
        } returns HandReferenceSaveOutcome.Saved(saved)
        coEvery { preferences.save(saved) } just runs
        every { fileStore.purgeObsoleteHandFiles(saved.localPath) } just runs
        coEvery { fileStore.clearCaptureCache() } just runs

        val outcome = repository.save(
            sourceAbsolutePath = "/tmp/capture.jpg",
            capturedAtEpochMs = FIXED_NOW_MS,
            source = HandReferenceSource.USER,
        )

        assertThat(outcome).isEqualTo(HandReferenceSaveOutcome.Saved(saved))
        coVerify(exactly = 1) { fileStore.clearCaptureCache() }
        coVerify(exactly = 1) { preferences.save(saved) }
    }

    @Test
    fun `save clears staging cache even when persist is rejected`() = runTest {
        every {
            fileStore.persist(
                sourceAbsolutePath = "/tmp/tiny.jpg",
                capturedAtEpochMs = FIXED_NOW_MS,
                source = HandReferenceSource.USER,
                sampleId = null,
            )
        } returns HandReferenceSaveOutcome.Rejected(HandReferenceRejection.TOO_SMALL)
        coEvery { fileStore.clearCaptureCache() } just runs

        val outcome = repository.save(
            sourceAbsolutePath = "/tmp/tiny.jpg",
            capturedAtEpochMs = FIXED_NOW_MS,
            source = HandReferenceSource.USER,
        )

        assertThat(outcome).isEqualTo(HandReferenceSaveOutcome.Rejected(HandReferenceRejection.TOO_SMALL))
        coVerify(exactly = 1) { fileStore.clearCaptureCache() }
        coVerify(exactly = 0) { preferences.save(any()) }
    }

    @Test
    fun `concurrent saves keep datastore path pointing at a live file`() = runTest {
        val files = Collections.synchronizedSet(mutableSetOf<String>())
        val stored = AtomicReference<HandReference?>(null)
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        every {
            fileStore.persist(
                sourceAbsolutePath = any(),
                capturedAtEpochMs = any(),
                source = any(),
                sampleId = any(),
            )
        } answers {
            val timestamp = invocation.args[1] as Long
            val path = "/files/hand_reference/hand_$timestamp.jpg"
            files += path
            HandReferenceSaveOutcome.Saved(
                HandReference(
                    localPath = path,
                    capturedAtEpochMs = timestamp,
                    source = invocation.args[2] as HandReferenceSource,
                    sampleId = invocation.args[3] as String?,
                ),
            )
        }
        coEvery { preferences.save(any()) } coAnswers {
            val overlapping = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { current -> maxOf(current, overlapping) }
            delay(PREFS_SAVE_OVERLAP_MS)
            stored.set(firstArg())
            inFlight.decrementAndGet()
        }
        every { fileStore.purgeObsoleteHandFiles(any()) } answers {
            val keep = firstArg<String>()
            files.removeAll { it != keep }
        }
        coEvery { fileStore.clearCaptureCache() } just runs

        val first = async {
            repository.save(
                sourceAbsolutePath = "/tmp/one.jpg",
                capturedAtEpochMs = FIRST_SAVE_MS,
                source = HandReferenceSource.USER,
            )
        }
        val second = async {
            repository.save(
                sourceAbsolutePath = "/tmp/two.jpg",
                capturedAtEpochMs = SECOND_SAVE_MS,
                source = HandReferenceSource.USER,
            )
        }
        first.await()
        second.await()

        val kept = stored.get()
        assertThat(kept).isNotNull()
        assertThat(files).contains(kept!!.localPath)
        assertThat(maxInFlight.get()).isEqualTo(1)
    }

    private companion object {
        const val FIXED_NOW_MS = 1_720_000_000_123L
        const val FIRST_SAVE_MS = 1_720_000_000_001L
        const val SECOND_SAVE_MS = 1_720_000_000_002L
        const val PREFS_SAVE_OVERLAP_MS = 40L
    }
}
