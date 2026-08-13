package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandInferenceEnhancer
import br.com.unhasdequecor.data.vision.HandLandmarkQuality
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.HandPresenceScoring
import kotlin.math.hypot

/**
 * Diagnóstico de falha a partir de scores já disponíveis no pipeline
 * (presence, tip-span, extent da mão, luminância / highlights).
 *
 * Puro e testável em JVM — não depende de Bitmap Android.
 */
object DetectionFailureDiagnostics {
    /** Extent da mão (bbox dos 21 pontos) abaixo → mão longe / pequena. */
    const val HAND_EXTENT_TOO_SMALL = 0.12f

    /** Tip-span abaixo → punho / ângulo ruim. */
    const val TIP_SPAN_BAD_ANGLE = HandLandmarkQuality.MIN_TIP_SPAN_FOR_EARLY_STOP

    /** Média de luminância (0–255) abaixo → cena escura. */
    const val MEAN_LUMA_TOO_DARK = 55f

    /** Fração de highlights acima → flash / estouro. */
    const val HIGHLIGHT_SHARE_GLARE = 0.12f

    /**
     * Sem landmarks MediaPipe: só iluminação da imagem.
     */
    fun fromImageStats(
        meanLuminance: Float,
        highlightShare: Float,
    ): DetectionFailureReason {
        val mean = meanLuminance.coerceIn(0f, 255f)
        val glare = highlightShare.coerceIn(0f, 1f)
        return when {
            glare >= HIGHLIGHT_SHARE_GLARE -> DetectionFailureReason.ExcessiveGlare
            mean < MEAN_LUMA_TOO_DARK -> DetectionFailureReason.TooDark
            else -> DetectionFailureReason.Generic
        }
    }

    /**
     * Landmarks presentes (mesmo com presence rejeitada ou WEAK).
     *
     * @param barrier qual piso rejeitou (quando aplicável) — melhora o log e o tip.
     * @param tipPresence tip-presence média (glare nas pontas); null se indisponível.
     * @param paintableNailCount unhas acima do piso de pintura.
     * @param hasMappableAnchors mapper produziu ≥2 placas.
     */
    fun fromLandmarks(
        landmarks: HandLandmarks,
        reliability: TryOnReliability,
        barrier: RejectionBarrier = RejectionBarrier.NONE,
        tipPresence: Float? = null,
        paintableNailCount: Int = 0,
        hasMappableAnchors: Boolean = false,
        meanLuminance: Float? = null,
        highlightShare: Float? = null,
    ): DetectionFailureReason {
        val extent = handExtentNorm(landmarks)
        val tipSpan = HandLandmarkQuality.tipSpanNorm(landmarks.points)
        val glareShare = highlightShare?.coerceIn(0f, 1f)
        val mean = meanLuminance?.coerceIn(0f, 255f)
        val tipGlare =
            tipPresence != null && tipPresence < HandPresenceScoring.TIP_GLARE_MAX

        // Prioridade: sinais físicos da cena → geometria → unhas → genérico.
        if (glareShare != null && glareShare >= HIGHLIGHT_SHARE_GLARE) {
            return DetectionFailureReason.ExcessiveGlare
        }
        if (tipGlare && reliability != TryOnReliability.STRONG) {
            return DetectionFailureReason.ExcessiveGlare
        }
        if (mean != null && mean < MEAN_LUMA_TOO_DARK) {
            return DetectionFailureReason.TooDark
        }
        if (extent < HAND_EXTENT_TOO_SMALL ||
            barrier == RejectionBarrier.HAND_PRESENCE && extent < HAND_EXTENT_TOO_SMALL * 1.5f
        ) {
            return DetectionFailureReason.HandTooFar
        }
        if (tipSpan < TIP_SPAN_BAD_ANGLE) {
            return DetectionFailureReason.BadAngle
        }
        if (reliability == TryOnReliability.REJECTED) {
            return when (barrier) {
                RejectionBarrier.HAND_PRESENCE -> DetectionFailureReason.Generic
                RejectionBarrier.ROI -> DetectionFailureReason.NoNailVisible
                RejectionBarrier.NAIL_COMBINED -> DetectionFailureReason.NoNailVisible
                RejectionBarrier.NONE -> DetectionFailureReason.Generic
            }
        }
        if (paintableNailCount == 0 && !hasMappableAnchors) {
            return DetectionFailureReason.NoNailVisible
        }
        if (paintableNailCount == 0) {
            return DetectionFailureReason.NoNailVisible
        }
        // WEAK / APPROXIMATE com unhas: tip de ângulo se span só “ok”.
        if (reliability == TryOnReliability.WEAK &&
            tipSpan < HandLandmarkQuality.GOOD_OPEN_TIP_SPAN
        ) {
            return DetectionFailureReason.BadAngle
        }
        return DetectionFailureReason.Generic
    }

    /** Diâmetro normalizado do bbox de todos os landmarks (0–1). */
    fun handExtentNorm(landmarks: HandLandmarks): Float {
        val pts = landmarks.points
        if (pts.isEmpty()) return 0f
        var minX = 1f
        var maxX = 0f
        var minY = 1f
        var maxY = 0f
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return hypot((maxX - minX).toDouble(), (maxY - minY).toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    /**
     * Média de luminância amostrada (ARGB packed).
     * Reutiliza [HandInferenceEnhancer.luminance].
     */
    fun meanLuminanceArgb(pixels: IntArray, sampleStep: Int = 8): Float {
        require(pixels.isNotEmpty())
        require(sampleStep >= 1)
        var sum = 0L
        var n = 0
        var i = 0
        while (i < pixels.size) {
            sum += HandInferenceEnhancer.luminance(pixels[i])
            n += 1
            i += sampleStep
        }
        return if (n == 0) 0f else sum.toFloat() / n.toFloat()
    }
}

/** Qual barreira do [DetectionConfidenceFloor] derrubou o caminho. */
enum class RejectionBarrier {
    NONE,
    HAND_PRESENCE,
    ROI,
    NAIL_COMBINED,
}
