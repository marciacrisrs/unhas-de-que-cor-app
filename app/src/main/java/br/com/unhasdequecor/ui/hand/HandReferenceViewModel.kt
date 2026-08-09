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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    @ApplicationContext private val context: Context,
    observeHandReference: ObserveHandReferenceUseCase,
    private val saveHandReference: SaveHandReferenceUseCase,
    private val clearHandReference: ClearHandReferenceUseCase,
    private val fileStore: HandReferenceFileStore,
) : ViewModel() {

    private val isSaving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HandReferenceUiState> = combine(
        observeHandReference(),
        isSaving,
        message,
    ) { reference, saving, msg ->
        HandReferenceUiState(
            reference = reference,
            isSaving = saving,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HandReferenceUiState(),
    )

    fun createCameraCaptureFile(): File = fileStore.createCameraCaptureFile()

    fun importFromGallery(uri: Uri) {
        viewModelScope.launch {
            isSaving.value = true
            message.value = null
            val prepared = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    fileStore.copyUriStreamToCache(input)
                }
            }.getOrNull()
            if (prepared == null) {
                isSaving.value = false
                message.value = messageFor(HandReferenceRejection.IO_ERROR)
                return@launch
            }
            persist(prepared.absolutePath)
        }
    }

    fun importFromCameraCapture(file: File) {
        viewModelScope.launch {
            isSaving.value = true
            message.value = null
            persist(file.absolutePath)
        }
    }

    fun clear() {
        viewModelScope.launch {
            clearHandReference()
            message.value = "Foto da mão removida."
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private suspend fun persist(path: String) {
        when (val outcome = saveHandReference(path)) {
            is HandReferenceSaveOutcome.Saved -> {
                message.value = "Mão cadastrada com sucesso."
            }
            is HandReferenceSaveOutcome.Rejected -> {
                message.value = messageFor(outcome.reason)
            }
        }
        isSaving.value = false
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
