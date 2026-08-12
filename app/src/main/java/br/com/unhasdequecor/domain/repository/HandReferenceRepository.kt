package br.com.unhasdequecor.domain.repository

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import kotlinx.coroutines.flow.Flow

/**
 * Fonte de verdade para a mão de referência usada no try-on. Mantém o domínio livre de
 * `android.net.Uri`/`Context`: URIs e assets de imagem trafegam como `String` (caminho absoluto
 * ou content URI serializado), com a resolução de plataforma isolada na implementação `data`.
 */
interface HandReferenceRepository {
    fun observe(): Flow<HandReference?>

    suspend fun save(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
        source: HandReferenceSource = HandReferenceSource.USER,
        sampleId: String? = null,
    ): HandReferenceSaveOutcome

    suspend fun clear()

    /**
     * Garante uma mão de referência (amostra padrão) quando não há foto válida.
     * @return a referência vigente após o ensure, ou null se falhar ao materializar a amostra.
     */
    suspend fun ensureDefaultSample(): HandReference?

    /**
     * Substitui a foto atual pela amostra padrão sem passar por estado vazio.
     * Usado ao “remover” a foto da usuária.
     */
    suspend fun resetToDefaultSample(): HandReference?

    /**
     * Copia o conteúdo de um content URI (serializado como [String]) para o cache de captura.
     * @return o caminho absoluto do arquivo em staging, ou null se a leitura/gravação falhar.
     */
    suspend fun stageFromContentUri(uriString: String): String?

    /**
     * Copia um asset de amostra (ex.: catálogo de mãos) para o cache de captura.
     * @return o caminho absoluto do arquivo em staging, ou null se a cópia falhar.
     */
    suspend fun stageSampleAsset(assetPath: String): String?

    /** Gera um caminho absoluto único no cache de captura para a próxima foto da câmera. */
    fun createCameraCapturePath(): String

    /** Limpa o cache de staging de forma assíncrona (uso normal em corrotina). */
    suspend fun clearStagingCache()

    /** Limpeza síncrona do cache de staging, para teardown (ex.: `ViewModel.onCleared`). */
    fun clearStagingCacheNow()
}
