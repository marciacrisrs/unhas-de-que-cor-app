package br.com.unhasdequecor.ui.hand

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandReferenceScreen(
    onBack: () -> Unit,
    onHandSelected: () -> Unit = onBack,
    viewModel: HandReferenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    HandReferenceMessageEffects(
        message = state.message,
        navigateHome = state.navigateHome,
        cameraPermissionDenied = cameraPermissionDenied,
        snackbarHostState = snackbarHostState,
        onMessageConsumed = viewModel::consumeMessage,
        onNavigateHomeConsumed = {
            viewModel.consumeNavigateHome()
            onHandSelected()
        },
        onPermissionDeniedConsumed = { cameraPermissionDenied = false },
    )

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
            title = { Text("Voltar para a mão de exemplo?") },
            text = {
                Text("Sua foto sai e o try-on usa de novo uma das mãos de referência.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemove) {
                    Text("Usar exemplo")
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
        actions = HandReferenceActions(
            onBack = onBack,
            onOpenSamplePicker = viewModel::openSamplePicker,
            onOpenReplaceSheet = viewModel::openReplaceSheet,
            onOpenRemoveConfirm = viewModel::openRemoveConfirm,
            onConfirmUserPhoto = viewModel::confirmPendingUserPhoto,
            onDiscardUserPhoto = viewModel::discardPendingUserPhoto,
        ),
    )
}
