package br.com.unhasdequecor.ui.tryon

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import br.com.unhasdequecor.BuildConfig
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.DetectedNail
import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import br.com.unhasdequecor.data.vision.nail.DetectionFailureReason
import br.com.unhasdequecor.data.vision.nail.LiveTryOnClaimMapper
import br.com.unhasdequecor.data.vision.nail.NailDetectionSnapshot
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import br.com.unhasdequecor.data.vision.nail.TryOnPreviewClaim
import br.com.unhasdequecor.data.vision.nail.TryOnReliability
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class LiveTryOnUiState(
    val colorName: String = "",
    val polishColor: Color = Color.Unspecified,
    val overlay: Bitmap? = null,
    val claim: TryOnPreviewClaim = TryOnPreviewClaim.LOADING,
    val failureReason: DetectionFailureReason? = null,
    val nails: List<DetectedNail> = emptyList(),
    val landmarks: HandLandmarks? = null,
    val showDebug: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LiveTryOnViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: ColorCatalogRepository,
    private val pipeline: NailTryOnPipeline,
) : ViewModel() {

    private val processing = AtomicBoolean(false)
    private val sessionClosed = AtomicBoolean(false)
    private val sessionReleased = AtomicBoolean(false)
    private var displayedOverlay: Bitmap? = null
    private var retiringOverlay: Bitmap? = null

    private val _uiState = MutableStateFlow(LiveTryOnUiState())
    val uiState: StateFlow<LiveTryOnUiState> = _uiState.asStateFlow()

    init {
        pipeline.debugEnabled = BuildConfig.DEBUG && BuildConfig.DEBUG_NAIL_OVERLAY
        val colorId = savedStateHandle.get<String>(COLOR_ID_KEY).orEmpty()
        val color = catalog.getById(colorId)
        if (color == null) {
            _uiState.value = LiveTryOnUiState(
                errorMessage = "Não encontramos essa cor para o try-on ao vivo.",
            )
        } else {
            _uiState.value = LiveTryOnUiState(
                colorName = color.name,
                polishColor = Color(color.hex),
                showDebug = pipeline.debugEnabled,
            )
        }
    }

    fun consumeFrame(frame: Bitmap) {
        if (_uiState.value.errorMessage != null ||
            sessionClosed.get() ||
            !processing.compareAndSet(false, true)
        ) {
            recycleQuietly(frame)
            return
        }
        try {
            if (sessionClosed.get()) {
                recycleQuietly(frame)
                return
            }
            interpret(frame)
        } finally {
            try {
                if (sessionClosed.get()) {
                    releaseSession()
                }
            } finally {
                processing.set(false)
            }
        }
    }

    fun onCameraUnavailable() {
        sessionClosed.set(true)
        _uiState.update { current ->
            current.copy(
                errorMessage = current.errorMessage ?: CAMERA_UNAVAILABLE_MESSAGE,
                overlay = null,
            )
        }
        if (!processing.get()) {
            releaseSession()
        }
    }

    override fun onCleared() {
        sessionClosed.set(true)
        if (!processing.get()) {
            releaseSession()
        }
        super.onCleared()
    }

    internal fun releaseSession() {
        sessionClosed.set(true)
        if (!sessionReleased.compareAndSet(false, true)) return
        pipeline.resetTracking()
        recycleQuietly(retiringOverlay)
        recycleQuietly(displayedOverlay)
        retiringOverlay = null
        displayedOverlay = null
    }

    private fun interpret(frame: Bitmap) {
        val snapshot = pipeline.detect(frame, stabilize = true)
        if (snapshot == null || snapshot.reliability == TryOnReliability.REJECTED) {
            publish(
                overlay = null,
                claim = TryOnPreviewClaim.NOT_DETECTED,
                reason = snapshot?.failureReason ?: DetectionFailureReason.Generic,
                nails = emptyList(),
                landmarks = snapshot?.landmarks,
            )
            releaseUnused(frame, snapshot, overlay = null)
            return
        }
        val preview = decision(snapshot, paintedViaEllipse = false)
        if (!preview.showOverlay) {
            publish(
                overlay = null,
                claim = preview.claim,
                reason = preview.reason,
                nails = emptyList(),
                landmarks = snapshot.landmarks,
            )
            releaseUnused(frame, snapshot, overlay = null)
            return
        }
        val result = pipeline.recolor(snapshot, _uiState.value.polishColor)
        val painted = decision(snapshot, result.paintedViaEllipse)
        val overlay = result.bitmap.takeIf { painted.showOverlay }
        publish(
            overlay = overlay,
            claim = painted.claim,
            reason = painted.reason,
            nails = result.nails,
            landmarks = result.landmarks,
        )
        releaseUnused(frame, snapshot, overlay)
    }

    private fun decision(
        snapshot: NailDetectionSnapshot,
        paintedViaEllipse: Boolean,
    ) = LiveTryOnClaimMapper.decide(
        reliability = snapshot.reliability,
        paintableNailCount = DetectionConfidenceFloor.countPaintable(snapshot.nails),
        fullQualityNailCount = DetectionConfidenceFloor.countFullQuality(snapshot.nails),
        hasMappableAnchors = LiveTryOnClaimMapper.hasMappableAnchors(snapshot.landmarks),
        paintedViaEllipse = paintedViaEllipse,
        failureReason = snapshot.failureReason,
    )

    private fun publish(
        overlay: Bitmap?,
        claim: TryOnPreviewClaim,
        reason: DetectionFailureReason?,
        nails: List<DetectedNail>,
        landmarks: HandLandmarks?,
    ) {
        if (overlay !== displayedOverlay) {
            recycleQuietly(retiringOverlay)
            retiringOverlay = displayedOverlay
            displayedOverlay = overlay
        }
        _uiState.update { current ->
            current.copy(
                overlay = overlay,
                claim = claim,
                failureReason = reason,
                nails = nails,
                landmarks = landmarks,
                showDebug = pipeline.debugEnabled,
            )
        }
    }

    private fun releaseUnused(
        frame: Bitmap,
        snapshot: NailDetectionSnapshot?,
        overlay: Bitmap?,
    ) {
        val keep = setOfNotNull(overlay, displayedOverlay, retiringOverlay)
        val working = snapshot?.workingBitmap
        if (snapshot?.ownsWorkingBitmap == true && working != null && working !in keep) {
            recycleQuietly(working)
        }
        if (frame !in keep) {
            recycleQuietly(frame)
        }
    }

    private fun recycleQuietly(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private companion object {
        const val COLOR_ID_KEY = "colorId"
        const val CAMERA_UNAVAILABLE_MESSAGE =
            "Não foi possível abrir a câmera para o try-on ao vivo."
    }
}
