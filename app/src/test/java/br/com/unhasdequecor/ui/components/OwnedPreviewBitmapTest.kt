package br.com.unhasdequecor.ui.components

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Test

class OwnedPreviewBitmapTest {

    @Test
    fun `unprotected candidate is owned and not copied`() {
        val candidate = bitmap()
        val protected = bitmap()
        var copied = false

        val claimed = claimPreviewBitmap(candidate, listOf(protected)) { copied = true; it }

        assertThat(claimed.bitmap).isSameInstanceAs(candidate)
        assertThat(claimed.owned).isTrue()
        assertThat(copied).isFalse()
    }

    @Test
    fun `protected candidate keeps a successful copy`() {
        val candidate = bitmap()
        val copy = bitmap()

        val claimed = claimPreviewBitmap(candidate, listOf(candidate)) { copy }

        assertThat(claimed.bitmap).isSameInstanceAs(copy)
        assertThat(claimed.owned).isTrue()
    }

    @Test
    fun `oom copy of protected candidate is not owned so dispose cannot recycle the photo`() {
        val candidate = bitmap()

        val claimed = claimPreviewBitmap(candidate, listOf(candidate)) { null }

        assertThat(claimed.bitmap).isSameInstanceAs(candidate)
        assertThat(claimed.owned).isFalse()
    }

    @Test
    fun `recycled candidate is not owned`() {
        val candidate = bitmap(recycled = true)

        val claimed = claimPreviewBitmap(candidate, emptyList()) {
            error("must not copy a recycled bitmap")
        }

        assertThat(claimed.bitmap).isSameInstanceAs(candidate)
        assertThat(claimed.owned).isFalse()
    }

    @Test
    fun `dispose recycles only owned previews`() {
        val owned = bitmap()
        val aliased = bitmap()

        recycleIfOwned(owned, owned = true)
        recycleIfOwned(aliased, owned = false)

        verify(exactly = 1) { owned.recycle() }
        verify(exactly = 0) { aliased.recycle() }
    }

    @Test
    fun `assets are unusable after the base disposer recycles them mid-paint`() {
        val decoded = bitmap()
        val working = bitmap()
        assertThat(tryOnAssetsUsable(decoded, working)).isTrue()

        every { working.isRecycled } returns true
        assertThat(tryOnAssetsUsable(decoded, working)).isFalse()
    }

    private fun bitmap(recycled: Boolean = false): Bitmap = mockk(relaxed = true) {
        every { isRecycled } returns recycled
        every { recycle() } just runs
    }
}
