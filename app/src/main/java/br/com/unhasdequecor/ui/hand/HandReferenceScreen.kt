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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.domain.model.HandSampleOption
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

    fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    HandReferenceMessageEffects(
        message = state.message,
        cameraPermissionDenied = cameraPermissionDenied,
        snackbarHostState = snackbarHostState,
        onMessageConsumed = viewModel::consumeMessage,
        onPermissionDeniedConsumed = { cameraPermissionDenied = false },
    )

    if (state.showReplaceSheet) {
        ReplaceHandSheet(
            onGallery = {
                viewModel.dismissReplaceSheet()
                openGallery()
            },
            onCamera = {
                viewModel.dismissReplaceSheet()
                requestOrOpenCamera()
            },
            onSample = {
                viewModel.dismissReplaceSheet()
                viewModel.openSamplePicker()
            },
            onDismiss = viewModel::dismissReplaceSheet,
        )
    }

    if (state.showSamplePicker) {
        HandSamplePickerSheet(
            options = state.sampleOptions,
            pendingSampleId = state.pendingSampleId,
            onSelectPending = viewModel::selectPendingSample,
            onConfirm = viewModel::confirmPendingSample,
            onDismiss = viewModel::dismissSamplePicker,
        )
    }

    if (state.showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveConfirm,
            title = { Text("Remover foto da mão?") },
            text = { Text("Você poderá cadastrar outra depois.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemove) {
                    Text("Remover")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveConfirm) {
                    Text("Cancelar")
                }
            },
        )
    }

    HandReferenceScaffold(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPickGallery = ::openGallery,
        onOpenCamera = ::requestOrOpenCamera,
        onOpenSamplePicker = viewModel::openSamplePicker,
        onOpenReplaceSheet = viewModel::openReplaceSheet,
        onOpenRemoveConfirm = viewModel::openRemoveConfirm,
        onConfirmUserPhoto = viewModel::confirmPendingUserPhoto,
        onDiscardUserPhoto = viewModel::discardPendingUserPhoto,
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
    onOpenSamplePicker: () -> Unit,
    onOpenReplaceSheet: () -> Unit,
    onOpenRemoveConfirm: () -> Unit,
    onConfirmUserPhoto: () -> Unit,
    onDiscardUserPhoto: () -> Unit,
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
            if (state.isConfirmingUserPhoto) {
                UserPhotoConfirmContent(
                    path = state.pendingUserPreviewPath.orEmpty(),
                    enabled = !state.isSaving,
                    onConfirm = onConfirmUserPhoto,
                    onDiscard = onDiscardUserPhoto,
                )
            } else {
                HandReferenceContent(
                    state = state,
                    onPickGallery = onPickGallery,
                    onOpenCamera = onOpenCamera,
                    onOpenSamplePicker = onOpenSamplePicker,
                    onOpenReplaceSheet = onOpenReplaceSheet,
                    onOpenRemoveConfirm = onOpenRemoveConfirm,
                )
            }
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
private fun UserPhotoConfirmContent(
    path: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "É esta a mão que você quer usar?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Confira se as unhas aparecem bem e a luz está boa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HandPreview(
            path = path,
            revision = path.hashCode().toLong(),
            isSample = false,
            sampleTitle = null,
            empty = false,
        )
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryCtaButton(
            text = "OK, usar esta",
            onClick = onConfirm,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Escolher outra", onClick = onDiscard)
    }
}

@Composable
private fun HandReferenceContent(
    state: HandReferenceUiState,
    onPickGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSamplePicker: () -> Unit,
    onOpenReplaceSheet: () -> Unit,
    onOpenRemoveConfirm: () -> Unit,
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
            text = if (state.hasReference) {
                if (state.isSample) {
                    "Você está com uma mão de exemplo. Troque pela sua para o try-on ficar mais fiel."
                } else {
                    "Esta é a mão que vai experimentar as cores."
                }
            } else {
                "Sem foto agora? Escolha um exemplo pelo tom de pele."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.hasReference) {
            Spacer(modifier = Modifier.height(12.dp))
            CaptureGuideTips()
        }
        Spacer(modifier = Modifier.height(16.dp))
        HandPreview(
            path = state.reference?.localPath,
            revision = state.reference?.capturedAtEpochMs ?: 0L,
            isSample = state.isSample,
            sampleTitle = state.sampleTitle,
            empty = !state.hasReference,
        )
        Spacer(modifier = Modifier.height(20.dp))
        HandReferenceActions(
            hasReference = state.hasReference,
            isSample = state.isSample,
            enabled = !state.isSaving,
            onPickGallery = onPickGallery,
            onOpenCamera = onOpenCamera,
            onOpenSamplePicker = onOpenSamplePicker,
            onOpenReplaceSheet = onOpenReplaceSheet,
            onOpenRemoveConfirm = onOpenRemoveConfirm,
        )
    }
}

@Composable
private fun CaptureGuideTips() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("Boa luz", "Fundo simples", "Unhas à mostra").forEach { tip ->
            Text(
                text = tip,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HandReferenceActions(
    hasReference: Boolean,
    isSample: Boolean,
    enabled: Boolean,
    onPickGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSamplePicker: () -> Unit,
    onOpenReplaceSheet: () -> Unit,
    onOpenRemoveConfirm: () -> Unit,
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
        SecondaryCtaButton(text = "Usar foto de exemplo", onClick = onOpenSamplePicker)
    } else {
        if (isSample) {
            PrimaryCtaButton(
                text = "Usar minha mão",
                onClick = onOpenReplaceSheet,
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryCtaButton(text = "Trocar exemplo", onClick = onOpenSamplePicker)
        } else {
            PrimaryCtaButton(
                text = "Trocar foto",
                onClick = onOpenReplaceSheet,
                enabled = enabled,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Remover foto", onClick = onOpenRemoveConfirm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceHandSheet(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onSample: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Trocar foto",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Escolha de onde vem a nova mão.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryCtaButton(text = "Galeria", onClick = onGallery)
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryCtaButton(text = "Câmera", onClick = onCamera)
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryCtaButton(text = "Mão de exemplo", onClick = onSample)
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryCtaButton(text = "Cancelar", onClick = onDismiss)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandSamplePickerSheet(
    options: List<HandSampleOption>,
    pendingSampleId: String?,
    onSelectPending: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Escolha pelo tom de pele",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A cor do esmalte no exemplo é só referência visual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                items(options, key = { it.id }) { option ->
                    HandSampleCard(
                        option = option,
                        selected = option.id == pendingSampleId,
                        onClick = { onSelectPending(option.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryCtaButton(
                text = "OK, usar esta",
                onClick = onConfirm,
                enabled = pendingSampleId != null,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryCtaButton(text = "Cancelar", onClick = onDismiss)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HandSampleCard(
    option: HandSampleOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, option.assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(option.assetPath).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = Modifier
            .clip(SoftSurfaceShape)
            .border(if (selected) 2.5.dp else 1.dp, borderColor, SoftSurfaceShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Exemplo ${option.title}"
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(SoftSurfaceShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selecionada",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(option.skinLabel, style = MaterialTheme.typography.labelLarge)
            Text(
                text = option.detailLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HandPreview(
    path: String?,
    revision: Long,
    isSample: Boolean,
    sampleTitle: String?,
    empty: Boolean,
) {
    val bitmap by produceState(
        initialValue = null as android.graphics.Bitmap?,
        path,
        revision,
    ) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(path)
            }
        }
    }

    val aspect = if (empty) 4f / 3f else 3f / 4f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .semantics {
                contentDescription = when {
                    bitmap != null && isSample -> "Mão de exemplo cadastrada"
                    bitmap != null -> "Pré-visualização da mão cadastrada"
                    else -> "Nenhuma foto da mão cadastrada"
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
            if (isSample) {
                Text(
                    text = sampleTitle?.let { "Exemplo · $it" } ?: "Mão de exemplo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(SoftSurfaceShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
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
                    text = "Galeria, câmera ou um exemplo por tom de pele.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
