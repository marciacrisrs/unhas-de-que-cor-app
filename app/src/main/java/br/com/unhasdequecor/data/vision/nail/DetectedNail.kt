package br.com.unhasdequecor.data.vision.nail

data class DetectedNail(
    val finger: Finger,
    val roi: NailRoi,
    val mask: NailMask,
    val confidence: Float,
)
