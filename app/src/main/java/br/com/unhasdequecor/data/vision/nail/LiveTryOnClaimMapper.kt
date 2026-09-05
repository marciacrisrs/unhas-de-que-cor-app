package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks

/**
 * Interpreta um frame Live com o mesmo contrato de honestidade do STILL:
 * FULL só quando o plano de render é FULL (máscara de placa, nunca elipse).
 */
data class LiveTryOnFrameDecision(
    val showOverlay: Boolean,
    val claim: TryOnPreviewClaim,
    val reason: DetectionFailureReason?,
)

object LiveTryOnClaimMapper {
    fun decide(
        reliability: TryOnReliability?,
        paintableNailCount: Int,
        fullQualityNailCount: Int,
        hasMappableAnchors: Boolean,
        failureReason: DetectionFailureReason?,
    ): LiveTryOnFrameDecision {
        val plan = TryOnHandReliability.planRender(
            reliability = reliability,
            paintableNailCount = paintableNailCount,
            fullQualityNailCount = fullQualityNailCount,
            hasMappableAnchors = hasMappableAnchors,
        )
        return when (plan.mode) {
            UserTryOnRenderMode.NONE -> LiveTryOnFrameDecision(
                showOverlay = false,
                claim = TryOnPreviewClaim.NOT_DETECTED,
                reason = failureReason,
            )
            UserTryOnRenderMode.APPROXIMATE -> LiveTryOnFrameDecision(
                showOverlay = plan.useNailMasks,
                claim = TryOnPreviewClaim.APPROXIMATE,
                reason = failureReason,
            )
            UserTryOnRenderMode.FULL -> LiveTryOnFrameDecision(
                showOverlay = true,
                claim = TryOnPreviewClaim.FULL_USER,
                reason = null,
            )
        }
    }

    fun hasMappableAnchors(landmarks: HandLandmarks?): Boolean {
        if (landmarks == null) return false
        return NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks.points.map { point ->
                NailLandmarkMapper.NormalizedPoint(point.x, point.y)
            },
            imageWidth = landmarks.imageWidth,
            imageHeight = landmarks.imageHeight,
        ) != null
    }
}
