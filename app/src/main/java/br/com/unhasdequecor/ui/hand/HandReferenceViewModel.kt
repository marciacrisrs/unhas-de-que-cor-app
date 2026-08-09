package br.com.unhasdequecor.ui.hand

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.data.local.hand.HandReferenceFileStore
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.usecase.ClearHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.SaveHandReferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HandReferenceUiState(
    val reference: HandReference? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class HandReferenceViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    observeHandReference: ObserveHandReferenceUseCase,
    private val saveHandReference: SaveHandReferenceUseCase,
    private val clearHandReference: ClearHandReferenceUseCase,
    private val fileStore: HandReferenceFileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HandReferenceUiState())
    val uiState: StateFlow<HandReferenceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeHandReference().collect { reference ->
                _uiState.update { it.copy(reference = reference) }
            }
        }
    }

    fun createCameraCaptureFile(): File = fileStore.createCameraCaptureFile()

    fun importFromGallery(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val prepared = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    fileStore.copyUriStreamToCache(input)
                }
            }.getOrNull()
            if (prepared == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = messageFor(HandReferenceRejection.IO_ERROR),
                    )
                }
                return@launch
            }
            persist(prepared.absolutePath)
        }
    }

    fun importFromCameraCapture(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            persist(file.absolutePath)
        }
    }

    fun clear() {
        viewModelScope.launch {
            clearHandReference()
            _uiState.update { it.copy(message = "Foto da mão removida.") }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun persist(path: String) {
        when (val outcome = saveHandReference(path)) {
            is HandReferenceSaveOutcome.Saved -> {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = "Mão cadastrada com sucesso.",
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
}
