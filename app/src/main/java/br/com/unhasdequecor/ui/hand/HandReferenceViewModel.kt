package br.com.unhasdequecor.ui.hand

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.model.HandSampleCatalog
import br.com.unhasdequecor.domain.model.HandSampleOption
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import br.com.unhasdequecor.domain.usecase.ClearHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.SaveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.UseSampleHandReferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class HandReferenceUiState(
    val reference: HandReference? = null,
    val sampleOptions: List<HandSampleOption> = HandSampleCatalog.options,
    val showSamplePicker: Boolean = false,
    val showReplaceSheet: Boolean = false,
    val showRemoveConfirm: Boolean = false,
    val pendingSampleId: String? = null,
    val pendingUserPreviewPath: String? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
    /** Após confirmar foto ou amostra — a UI deve voltar para a origem. */
    val navigateHome: Boolean = false,
    /** Mensagem curta exibida na Home após o retorno. */
    val homeFlashMessage: String? = null,
) {
    val hasReference: Boolean get() = reference != null
    val isSample: Boolean get() = reference?.source == HandReferenceSource.SAMPLE
    val sampleTitle: String?
        get() = reference?.sampleId?.let { HandSampleCatalog.findById(it)?.title }
    val isConfirmingUserPhoto: Boolean get() = pendingUserPreviewPath != null
}

@HiltViewModel
class HandReferenceViewModel @Inject constructor(
    observeHandReference: ObserveHandReferenceUseCase,
    private val saveHandReference: SaveHandReferenceUseCase,
    private val useSampleHandReference: UseSampleHandReferenceUseCase,
    private val clearHandReference: ClearHandReferenceUseCase,
    private val repository: HandReferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HandReferenceUiState())
    val uiState: StateFlow<HandReferenceUiState> = _uiState.asStateFlow()

    /**
     * Persistência confirmada precisa terminar mesmo se a usuária sair da tela:
     * o teardown cancelava o save e apagava o JPEG em staging no meio da leitura.
     */
    private val persistInFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            observeHandReference().collect { reference ->
                _uiState.update { it.copy(reference = reference) }
            }
        }
    }

    fun createCameraCaptureFile(): File = File(repository.createCameraCapturePath())

    fun openReplaceSheet() {
        _uiState.update {
            it.copy(showReplaceSheet = true, showSamplePicker = false, message = null)
        }
    }

    fun dismissReplaceSheet() {
        _uiState.update { it.copy(showReplaceSheet = false) }
    }

    fun openSamplePicker() {
        _uiState.update {
            it.copy(
                showSamplePicker = true,
                showReplaceSheet = false,
                pendingSampleId = it.reference?.sampleId,
                message = null,
            )
        }
    }

    fun dismissSamplePicker() {
        _uiState.update { it.copy(showSamplePicker = false, pendingSampleId = null) }
    }

    fun selectPendingSample(sampleId: String) {
        _uiState.update { it.copy(pendingSampleId = sampleId) }
    }

    fun confirmPendingSample() {
        val sampleId = _uiState.value.pendingSampleId ?: return
        if (_uiState.value.isSaving) return
        useSampleHand(sampleId)
    }

    fun openRemoveConfirm() {
        _uiState.update { it.copy(showRemoveConfirm = true) }
    }

    fun dismissRemoveConfirm() {
        _uiState.update { it.copy(showRemoveConfirm = false) }
    }

    fun confirmRemove() {
        if (!beginSave { it.copy(showRemoveConfirm = false) }) return
        viewModelScope.launch {
            val restored = clearHandReference()
            _uiState.update {
                it.copy(
                    // Atualiza de imediato: evita flash de empty enquanto o Flow observa.
                    reference = restored ?: it.reference,
                    isSaving = false,
                    message = if (restored != null) {
                        "Voltamos para a mão de referência."
                    } else {
                        "Não foi possível restaurar o exemplo. Tente de novo."
                    },
                )
            }
        }
    }

    fun importFromGallery(uri: Uri) {
        if (!beginSave {
                it.copy(
                    showSamplePicker = false,
                    showReplaceSheet = false,
                    pendingSampleId = null,
                )
            }
        ) {
            return
        }
        viewModelScope.launch {
            val prepared = repository.stageFromContentUri(uri.toString())
            if (prepared == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = messageFor(HandReferenceRejection.IO_ERROR),
                    )
                }
                return@launch
            }
            stageUserPhoto(prepared)
        }
    }

    fun importFromCameraCapture(file: File) {
        if (!beginSave {
                it.copy(
                    showSamplePicker = false,
                    showReplaceSheet = false,
                    pendingSampleId = null,
                )
            }
        ) {
            return
        }
        viewModelScope.launch {
            stageUserPhoto(file.absolutePath)
        }
    }

    fun confirmPendingUserPhoto() {
        val path = _uiState.value.pendingUserPreviewPath ?: return
        if (!beginConfirmedPersist()) return
        launchConfirmedPersist { persistUser(path) }
    }

    fun discardPendingUserPhoto() {
        viewModelScope.launch {
            repository.clearStagingCache()
            _uiState.update {
                it.copy(pendingUserPreviewPath = null, isSaving = false)
            }
        }
    }

    fun useSampleHand(sampleId: String) {
        val option = HandSampleCatalog.findById(sampleId) ?: return
        if (!beginConfirmedPersist {
                it.copy(
                    showSamplePicker = false,
                    showReplaceSheet = false,
                    pendingSampleId = null,
                    pendingUserPreviewPath = null,
                )
            }
        ) {
            return
        }
        launchConfirmedPersist {
            val prepared = repository.stageSampleAsset(option.assetPath)
            if (prepared == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = messageFor(HandReferenceRejection.IO_ERROR),
                    )
                }
                return@launchConfirmedPersist
            }
            when (val outcome = useSampleHandReference(option.id, prepared)) {
                is HandReferenceSaveOutcome.Saved -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = null,
                            navigateHome = true,
                            homeFlashMessage = "Exemplo salvo: ${option.title}.",
                        )
                    }
                }
                is HandReferenceSaveOutcome.Rejected -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = messageFor(outcome.reason),
                        )
                    }
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeNavigateHome() {
        _uiState.update { it.copy(navigateHome = false, homeFlashMessage = null) }
    }

    /**
     * Marca [HandReferenceUiState.isSaving] de forma síncrona para o segundo
     * toque (double-tap no confirm) não disparar outro persist.
     */
    private fun beginSave(
        transform: (HandReferenceUiState) -> HandReferenceUiState = { it },
    ): Boolean {
        var started = false
        _uiState.update { current ->
            if (current.isSaving) {
                started = false
                current
            } else {
                started = true
                transform(current.copy(isSaving = true, message = null))
            }
        }
        return started
    }

    /** [beginSave] + trava de teardown para persistência confirmada. */
    private fun beginConfirmedPersist(
        transform: (HandReferenceUiState) -> HandReferenceUiState = { it },
    ): Boolean {
        if (!persistInFlight.compareAndSet(false, true)) return false
        val started = beginSave(transform)
        if (!started) persistInFlight.set(false)
        return started
    }

    private fun launchConfirmedPersist(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { block() }
            } finally {
                persistInFlight.set(false)
            }
        }
    }

    private fun stageUserPhoto(path: String) {
        _uiState.update {
            it.copy(
                isSaving = false,
                pendingUserPreviewPath = path,
            )
        }
    }

    private suspend fun persistUser(path: String) {
        when (val outcome = saveHandReference(path, HandReferenceSource.USER)) {
            is HandReferenceSaveOutcome.Saved -> {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingUserPreviewPath = null,
                        message = null,
                        navigateHome = true,
                        homeFlashMessage = "Mão cadastrada com sucesso.",
                    )
                }
            }
            is HandReferenceSaveOutcome.Rejected -> {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = messageFor(outcome.reason),
                    )
                }
            }
        }
    }

    private fun messageFor(reason: HandReferenceRejection): String = when (reason) {
        HandReferenceRejection.INVALID_IMAGE ->
            "Não conseguimos ler essa imagem. Tente outra foto."
        HandReferenceRejection.TOO_SMALL ->
            "A foto está pequena demais. Use uma imagem com pelo menos 480px."
        HandReferenceRejection.TOO_LARGE ->
            "A foto é muito grande. Escolha uma imagem de até 15 MB."
        HandReferenceRejection.IO_ERROR ->
            "Não foi possível salvar a foto. Tente de novo."
    }

    override fun onCleared() {
        super.onCleared()
        if (!persistInFlight.get()) {
            repository.clearStagingCacheNow()
        }
    }
}
