package br.com.unhasdequecor.ui.hand

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun HandReferenceMessageEffects(
    message: String?,
    navigateHome: Boolean,
    cameraPermissionDenied: Boolean,
    snackbarHostState: SnackbarHostState,
    onMessageConsumed: () -> Unit,
    onNavigateHomeConsumed: () -> Unit,
    onPermissionDeniedConsumed: () -> Unit,
) {
    LaunchedEffect(navigateHome) {
        if (navigateHome) {
            onNavigateHomeConsumed()
        }
    }
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
