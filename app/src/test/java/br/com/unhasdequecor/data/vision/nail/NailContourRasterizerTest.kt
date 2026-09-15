package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailContourRasterizerTest {

    @Test
    fun `packed ARGB alpha is the high byte not the blue channel`() {
        assertThat(packedArgbAlpha(0xFF000000.toInt())).isEqualTo(255)
        assertThat(packedArgbAlpha(0x80FFFFFF.toInt())).isEqualTo(128)
        assertThat(packedArgbAlpha(0x00FFFFFF)).isEqualTo(0)
        // Opaque fill drawn as black/white has blue=0 or blue=255; the old
        // `pixel and 0xFF` read would treat black as empty coverage.
        assertThat(0xFF000000.toInt() and 0xFF).isEqualTo(0)
        assertThat(packedArgbAlpha(0xFF000000.toInt())).isNotEqualTo(0xFF000000.toInt() and 0xFF)
    }

    @Test
    fun `opaque ARGB fill keeps learned plate coverage`() {
        val width = 4
        val height = 4
        val original = ByteArray(width * height) { 255.toByte() }
        val raster = IntArray(width * height) { 0xFF000000.toInt() }

        val merged = mergeRasterWithSupport(original, raster, width, height)

        assertThat(merged.all { (it.toInt() and 0xFF) == 255 }).isTrue()
        val mask = NailMask(width, height, merged)
        assertThat(mask.filledRatio()).isEqualTo(1f)
    }

    @Test
    fun `reading the blue channel would wipe an opaque black contour`() {
        val width = 3
        val height = 3
        val original = ByteArray(width * height) { 255.toByte() }
        val raster = IntArray(width * height) { 0xFF000000.toInt() }

        val merged = mergeRasterWithSupport(original, raster, width, height)
        val mistakenBlue = raster.map { pixel -> (pixel and 0xFF).toByte() }.toByteArray()

        assertThat(merged.any { (it.toInt() and 0xFF) != 0 }).isTrue()
        assertThat(mistakenBlue.all { it.toInt() == 0 }).isTrue()
        assertThat(NailMask(width, height, mistakenBlue).filledRatio()).isEqualTo(0f)
    }

    @Test
    fun `empty raster does not invent coverage outside support`() {
        val width = 3
        val height = 3
        val original = ByteArray(width * height)
        original[4] = 255.toByte()
        val raster = IntArray(width * height)

        val merged = mergeRasterWithSupport(original, raster, width, height)

        assertThat(merged.all { it.toInt() == 0 }).isTrue()
    }

    @Test
    fun `antialiased edge keeps partial alpha inside one-pixel support`() {
        val width = 3
        val height = 3
        val original = ByteArray(width * height)
        original[4] = 255.toByte()
        val raster = IntArray(width * height)
        raster[4] = 0x80FFFFFF.toInt()
        raster[5] = 0x40FFFFFF.toInt()

        val merged = mergeRasterWithSupport(original, raster, width, height)

        assertThat(merged[4].toInt() and 0xFF).isEqualTo(128)
        assertThat(merged[5].toInt() and 0xFF).isEqualTo(64)
        assertThat(merged[0].toInt() and 0xFF).isEqualTo(0)
    }
}
