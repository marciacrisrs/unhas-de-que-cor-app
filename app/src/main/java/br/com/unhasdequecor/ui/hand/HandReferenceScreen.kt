package br.com.unhasdequecor.ui.hand

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.SecondaryCtaButton
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandReferenceScreen(
    onBack: () -> Unit,
    viewModel: HandReferenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromGallery(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            viewModel.importFromCameraCapture(file)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val file = viewModel.createCameraCaptureFile()
            pendingCameraFile = file
            cameraLauncher.launch(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                ),
            )
        } else {
            cameraPermissionDenied = true
        }
    }

    fun requestOrOpenCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val file = viewModel.createCameraCaptureFile()
            pendingCameraFile = file
            cameraLauncher.launch(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                ),
            )
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    HandReferenceMessageEffects(
        message = state.message,
        cameraPermissionDenied = cameraPermissionDenied,
        snackbarHostState = snackbarHostState,
        onMessageConsumed = viewModel::consumeMessage,
        onPermissionDeniedConsumed = { cameraPermissionDenied = false },
    )

    HandReferenceScaffold(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPickGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onOpenCamera = ::requestOrOpenCamera,
        onUseSample = viewModel::useSampleHand,
        onClear = viewModel::clear,
    )
}

@Composable
private fun HandReferenceMessageEffects(
    message: String?,
    cameraPermissionDenied: Boolean,
    snackbarHostState: SnackbarHostState,
    onMessageConsumed: () -> Unit,
    onPermissionDeniedConsumed: () -> Unit,
) {
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        onMessageConsumed()
    }
    LaunchedEffect(cameraPermissionDenied) {
        if (cameraPermissionDenied) {
            snackbarHostState.showSnackbar(
                "Permissão de câmera necessária para tirar a foto.",
            )
            onPermissionDeniedConsumed()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandReferenceScaffold(
    state: HandReferenceUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPickGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onUseSample: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text("Minha mão", style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
            HandReferenceContent(
                state = state,
                onPickGallery = onPickGallery,
                onOpenCamera = onOpenCamera,
                onUseSample = onUseSample,
                onClear = onClear,
            )
        }

        if (state.isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun HandReferenceContent(
    state: HandReferenceUiState,
    onPickGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onUseSample: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "Cadastre uma foto da sua mão para o try-on virtual.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Prefere começar sem foto? Use a mão de exemplo e troque pela sua depois. " +
                "Dica para a sua: fundo simples, boa luz e unhas à mostra.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        HandPreview(path = state.reference?.localPath)
        Spacer(modifier = Modifier.height(20.dp))
        HandReferenceActions(
            hasReference = state.reference != null,
            enabled = !state.isSaving,
            onPickGallery = onPickGallery,
            onOpenCamera = onOpenCamera,
            onUseSample = onUseSample,
            onClear = onClear,
        )
    }
}

@Composable
private fun HandReferenceActions(
    hasReference: Boolean,
    enabled: Boolean,
    onPickGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onUseSample: () -> Unit,
    onClear: () -> Unit,
) {
    if (!hasReference) {
        PrimaryCtaButton(
            text = "Escolher da galeria",
            onClick = onPickGallery,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Tirar foto", onClick = onOpenCamera)
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Usar foto de exemplo", onClick = onUseSample)
    } else {
        PrimaryCtaButton(
            text = "Substituir foto",
            onClick = onPickGallery,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Tirar nova foto", onClick = onOpenCamera)
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Usar foto de exemplo", onClick = onUseSample)
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Remover foto", onClick = onClear)
    }
}

@Composable
private fun HandPreview(path: String?) {
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, path) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(path)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .semantics {
                contentDescription = if (bitmap != null) {
                    "Pré-visualização da mão cadastrada"
                } else {
                    "Nenhuma foto da mão cadastrada"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val preview = bitmap
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = "Sua mão aparece aqui",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Galeria ou câmera — a foto fica só neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
