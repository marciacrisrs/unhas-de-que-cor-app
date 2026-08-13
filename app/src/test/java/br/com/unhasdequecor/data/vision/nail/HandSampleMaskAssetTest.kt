package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * Gate de assets: máscaras calibradas devem existir, casar dimensão com a foto
 * e ficar abaixo do teto de cobertura do recolorer (não pintam a imagem inteira).
 *
 * Não detecta elipse mal alinhada — isso exige remask + revisão vision.
 */
class HandSampleMaskAssetTest {

    @Test
    fun `calibrated mask assets match photo size and coverage gate`() {
        val assetsRoot = resolveAssetsRoot()
        val calibrated = listOf("clara_vermelho")
        for (id in calibrated) {
            assertThat(NailOverlayAnchors.hasMaskAsset(id)).isTrue()
            val maskFile = File(assetsRoot, "hand_nail_masks/$id.png")
            val photoFile = File(assetsRoot, "hand_samples/hand_sample_$id.webp")
            assertThat(maskFile.exists()).isTrue()
            assertThat(photoFile.exists()).isTrue()

            val mask = checkNotNull(ImageIO.read(maskFile))
            val (pw, ph) = webpSize(photoFile)
            assertThat(mask.width).isEqualTo(pw)
            assertThat(mask.height).isEqualTo(ph)

            var covered = 0
            var nonBlack = false
            val total = mask.width * mask.height
            for (y in 0 until mask.height) {
                for (x in 0 until mask.width) {
                    val argb = mask.getRGB(x, y)
                    val a = (argb ushr 24) and 0xFF
                    val r = (argb shr 16) and 0xFF
                    val g = (argb shr 8) and 0xFF
                    val b = argb and 0xFF
                    val gray = maxOf(r, g, b)
                    if (gray > 0) nonBlack = true
                    val cov = min(a, gray) / 255f
                    if (cov >= 0.08f) covered++
                }
            }
            val ratio = covered.toFloat() / total
            assertThat(nonBlack).isTrue()
            assertThat(ratio).isGreaterThan(0f)
            assertThat(ratio).isAtMost(0.18f)
        }
    }

    private fun resolveAssetsRoot(): File {
        val cwd = File(System.getProperty("user.dir")!!)
        val candidates = listOf(
            File(cwd, "src/main/assets"),
            File(cwd, "app/src/main/assets"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("assets root not found from $cwd")
    }

    /** Minimal WebP size from RIFF VP8 / VP8L / VP8X. */
    private fun webpSize(file: File): Pair<Int, Int> {
        val bytes = file.readBytes()
        require(bytes.size >= 30) { "webp too small: ${file.name}" }
        require(String(bytes, 0, 4) == "RIFF") { "not RIFF: ${file.name}" }
        require(String(bytes, 8, 4) == "WEBP") { "not WEBP: ${file.name}" }
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val fourcc = String(bytes, offset, 4)
            val size = u32le(bytes, offset + 4)
            val data = offset + 8
            when (fourcc) {
                "VP8X" -> {
                    val w = 1 + u24le(bytes, data + 4)
                    val h = 1 + u24le(bytes, data + 7)
                    return w to h
                }
                "VP8 " -> {
                    // lossy: bytes 6-9 of bitstream hold (width-1)/(height-1) 14-bit
                    val w = u16le(bytes, data + 6) and 0x3FFF
                    val h = u16le(bytes, data + 8) and 0x3FFF
                    return w to h
                }
                "VP8L" -> {
                    // signature 0x2f then 14-bit w-1 / h-1
                    require(bytes[data].toInt() and 0xFF == 0x2f)
                    val b0 = bytes[data + 1].toInt() and 0xFF
                    val b1 = bytes[data + 2].toInt() and 0xFF
                    val b2 = bytes[data + 3].toInt() and 0xFF
                    val b3 = bytes[data + 4].toInt() and 0xFF
                    val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    val w = (bits and 0x3FFF) + 1
                    val h = ((bits shr 14) and 0x3FFF) + 1
                    return w to h
                }
            }
            offset = data + size + (size and 1) // pad to even
        }
        error("no VP8 chunk in ${file.name}")
    }

    private fun u16le(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u24le(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or
            ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16)

    private fun u32le(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or
            ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or
            ((b[i + 3].toInt() and 0xFF) shl 24)
}
