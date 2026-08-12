package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import br.com.unhasdequecor.data.vision.HandLandmarkProcessor
import br.com.unhasdequecor.data.vision.HandLandmarkQuality
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.HandPresenceScoring
import br.com.unhasdequecor.data.vision.Handedness
import br.com.unhasdequecor.data.vision.OrientedHandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.NormPoint
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Bateria de condições difíceis (JVM): presence fraca, mid-presence, foto pequena,
 * punho/oclusão, flash (tip glare), ranking de variantes e recolor sem unhas.
 */
class TryOnDifficultConditionsTest {

    @Test
    fun flashTipGlare_presenceComHandednessClara_naoRejeita() {
        val score = HandPresenceScoring.score(handednessScore = 0.68f, tipPresence = 0.04f)
        assertThat(score).isAtLeast(DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT)
        assertThat(TryOnHandReliability.classify(score)).isNotEqualTo(TryOnReliability.REJECTED)
    }


    @Test
    fun presenceAbaixoDoFloor_detectRejeitaSemRoiNemSegmentacao() {
        val landmarkProcessor = mockk<HandLandmarkProcessor>()
        val roiEstimator = mockk<NailRoiEstimator>(relaxed = true)
        val segmenter = mockk<NailSegmenter>(relaxed = true)
        val colorApplier = mockk<NailColorApplier>(relaxed = true)
        val pipeline =
            NailTryOnPipeline(
                landmarkProcessor = landmarkProcessor,
                roiEstimator = roiEstimator,
                segmenter = segmenter,
                colorApplier = colorApplier,
                tracker = NailTracker(),
            )
        val source = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val rotated = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks =
            HandLandmarks(
                points = List(21) { NormPoint(0.5f, 0.5f) },
                imageWidth = 200,
                imageHeight = 300,
                presenceScore = DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT - 0.01f,
            )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(source)
        } returns OrientedHandLandmarks(bitmap = rotated, landmarks = landmarks)

        val snapshot = pipeline.detect(source, stabilize = false)

        assertThat(snapshot).isNull()
        verify(exactly = 1) { rotated.recycle() }
        verify(exactly = 0) { roiEstimator.estimateAll(any()) }
        verify(exactly = 0) { segmenter.segment(any(), any()) }
        verify(exactly = 0) { colorApplier.apply(any(), any(), any()) }
    }

    @Test
    fun midPresence_nuncaClaimFullMesmoComMuitasUnhasFortes() {
        val presence = DetectionConfidenceFloor.HAND_PRESENCE_STRONG - 0.02f
        assertThat(presence).isAtLeast(DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT)
        assertThat(TryOnHandReliability.classify(presence)).isEqualTo(TryOnReliability.WEAK)
        val nails =
            List(5) {
                DetectedNail(
                    finger = Finger.MIDDLE,
                    roi =
                        NailRoi(
                            finger = Finger.MIDDLE,
                            bounds = ImageCoordinates.PixelRect(0, 0, 10, 10),
                            polygon =
                                listOf(
                                    ImageCoordinates.PixelPoint(0f, 0f),
                                    ImageCoordinates.PixelPoint(10f, 0f),
                                    ImageCoordinates.PixelPoint(10f, 10f),
                                    ImageCoordinates.PixelPoint(0f, 10f),
                                ),
                            axisFromDip = ImageCoordinates.PixelPoint(5f, 10f),
                            axisToTip = ImageCoordinates.PixelPoint(5f, 0f),
                            lengthPx = 10f,
                            widthPx = 6f,
                            rotationDegrees = 0f,
                            geometricConfidence = 0.9f,
                        ),
                    mask =
                        NailMask(
                            width = 4,
                            height = 4,
                            alpha = ByteArray(16) { 255.toByte() },
                            originX = 0,
                            originY = 0,
                        ),
                    confidence = 0.95f,
                )
            }
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.WEAK,
                nails = nails,
                hasMappableAnchors = true,
            )
        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.mode).isNotEqualTo(UserTryOnRenderMode.FULL)
    }

    @Test
    fun tipDipRelativo_maoPendeNaoViraFacingPorAbsolutoBaixo() {
        // Proporção open-hand (tipDip/tipPip ≈ 0.40) em foto pequena: tipDip=12 < 16px.
        // Limiar antigo absoluto → facing errado; relativo → open.
        val tipDip = 12f
        val tipPip = 30f
        assertThat(tipDip).isLessThan(NailPlateCalibration.SHORT_TIP_DIP_PX)
        assertThat(
            NailPlateCalibration.isFacing(
                thumbMode = false,
                tipDipPx = tipDip,
                tipPipPx = tipPip,
            ),
        ).isFalse()
    }

    @Test
    fun tipDipRelativo_faceOnContinuaFacingEmEscala() {
        fun facingAtScale(scale: Float): Boolean {
            val tipDip = 6f * scale
            val tipPip = 120f * scale
            return NailPlateCalibration.isFacing(
                thumbMode = false,
                tipDipPx = tipDip,
                tipPipPx = tipPip,
            )
        }
        assertThat(facingAtScale(1f)).isTrue()
        assertThat(facingAtScale(0.5f)).isTrue()
        assertThat(facingAtScale(2f)).isTrue()
    }

    @Test
    fun tipDipRelativo_larguraFacingEstavelQuandoMaoDobra() {
        fun width(scale: Float): Float {
            val plate =
                NailPlateCalibration.plateFromPixels(
                    finger = Finger.MIDDLE,
                    tipX = 400f,
                    tipY = 354f * scale,
                    dipX = 400f,
                    dipY = 360f * scale,
                    pipX = 400f,
                    pipY = (354f + 120f) * scale,
                    mcpX = 400f,
                    mcpY = 600f * scale,
                )
            assertThat(plate.facing).isTrue()
            return plate.widthPx / scale
        }
        assertThat(width(1f)).isWithin(1f).of(width(0.5f))
    }

    @Test
    fun punhoComUmDedoEstendido_mapperNaoInventaCincoElipses() {
        val landmarks = MutableList(21) { NailLandmarkMapper.NormalizedPoint(0.5f, 0.55f) }
        fun set(
            i: Int,
            x: Float,
            y: Float,
        ) {
            landmarks[i] = NailLandmarkMapper.NormalizedPoint(x, y)
        }
        set(0, 0.50f, 0.90f)
        // Indicador estendido
        set(5, 0.42f, 0.62f)
        set(6, 0.40f, 0.48f)
        set(7, 0.39f, 0.34f)
        set(8, 0.38f, 0.20f)
        // Restante colapsado perto da palma
        for (base in listOf(9, 13, 17)) {
            set(base, 0.50f, 0.70f)
            set(base + 1, 0.50f, 0.68f)
            set(base + 2, 0.50f, 0.66f)
            set(base + 3, 0.50f, 0.64f)
        }
        // Polegar também colapsado (eixo MCP→tip curto)
        set(1, 0.50f, 0.72f)
        set(2, 0.50f, 0.71f)
        set(3, 0.50f, 0.705f)
        set(4, 0.50f, 0.70f)

        val anchors =
            NailLandmarkMapper.fromNormalizedLandmarks(
                landmarks = landmarks,
                imageWidth = 640,
                imageHeight = 480,
            )
        // Só o indicador fica usável → < MIN_PLAUSIBLE_NAILS → null (sem 5 elipses).
        assertThat(anchors).isNull()

        val hand =
            HandLandmarks(
                points = landmarks.map { NormPoint(it.x, it.y) },
                imageWidth = 640,
                imageHeight = 480,
                handedness = Handedness.RIGHT,
                presenceScore = 0.9f,
            )
        val rois = NailRoiEstimator().estimateAll(hand)
        assertThat(rois).hasSize(1)
        assertThat(rois.first().finger).isEqualTo(Finger.INDEX)
    }

    @Test
    fun rankingVariantes_prefereSpanAbertoMesmoComPresenceMenor() {
        val collapsed =
            HandLandmarks(
                points = List(21) { NormPoint(0.50f, 0.55f) },
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.90f,
            )
        val open =
            HandLandmarks(
                points = openHandPoints(),
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.58f,
            )
        assertThat(HandLandmarkQuality.tipSpanNorm(open.points))
            .isGreaterThan(HandLandmarkQuality.tipSpanNorm(collapsed.points))
        assertThat(HandLandmarkQuality.rankingScore(open))
            .isGreaterThan(HandLandmarkQuality.rankingScore(collapsed))
        assertThat(
            HandLandmarkQuality.shouldStopSearching(
                presenceScore = DetectionConfidenceFloor.HAND_PRESENCE_EARLY_STOP,
                tipSpan = 0f,
            ),
        ).isFalse()
        assertThat(
            HandLandmarkQuality.shouldStopSearching(
                presenceScore = DetectionConfidenceFloor.HAND_PRESENCE_EARLY_STOP,
                tipSpan = HandLandmarkQuality.MIN_TIP_SPAN_FOR_EARLY_STOP,
            ),
        ).isTrue()
    }

    @Test
    fun recolorComZeroUnhasELandmarksInvalidos_devolveWorking() {
        val landmarkProcessor = mockk<HandLandmarkProcessor>()
        val pipeline =
            NailTryOnPipeline(
                landmarkProcessor = landmarkProcessor,
                roiEstimator = mockk(relaxed = true),
                segmenter = mockk(relaxed = true),
                colorApplier = mockk(relaxed = true),
                tracker = NailTracker(),
            )
        val image =
            mockk<Bitmap>(relaxed = true) {
                every { width } returns 200
                every { height } returns 300
                every { isRecycled } returns false
            }
        val landmarks =
            HandLandmarks(
                points = List(21) { NormPoint(0.01f, 0.01f) },
                imageWidth = 200,
                imageHeight = 300,
            )
        val result =
            pipeline.recolor(
                NailDetectionSnapshot(
                    workingBitmap = image,
                    nails = emptyList(),
                    landmarks = landmarks,
                    ownsWorkingBitmap = false,
                    reliability = TryOnReliability.STRONG,
                ),
                Color.Red,
            )
        assertThat(result.bitmap).isSameInstanceAs(image)
    }

    private fun openHandPoints(): List<NormPoint> {
        val pts = MutableList(21) { NormPoint(0.5f, 0.5f) }
        pts[0] = NormPoint(0.50f, 0.78f)
        pts[2] = NormPoint(0.30f, 0.52f)
        pts[3] = NormPoint(0.28f, 0.48f)
        pts[4] = NormPoint(0.22f, 0.40f)
        pts[5] = NormPoint(0.41f, 0.50f)
        pts[6] = NormPoint(0.41f, 0.40f)
        pts[7] = NormPoint(0.40f, 0.36f)
        pts[8] = NormPoint(0.38f, 0.26f)
        pts[9] = NormPoint(0.50f, 0.50f)
        pts[10] = NormPoint(0.50f, 0.38f)
        pts[11] = NormPoint(0.50f, 0.34f)
        pts[12] = NormPoint(0.50f, 0.22f)
        pts[13] = NormPoint(0.59f, 0.50f)
        pts[14] = NormPoint(0.59f, 0.40f)
        pts[15] = NormPoint(0.60f, 0.36f)
        pts[16] = NormPoint(0.62f, 0.26f)
        pts[17] = NormPoint(0.68f, 0.52f)
        pts[18] = NormPoint(0.68f, 0.44f)
        pts[19] = NormPoint(0.70f, 0.40f)
        pts[20] = NormPoint(0.74f, 0.32f)
        return pts
    }
}
