package br.com.unhasdequecor.ui.hand

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun HandReferenceMessageEffects(
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
