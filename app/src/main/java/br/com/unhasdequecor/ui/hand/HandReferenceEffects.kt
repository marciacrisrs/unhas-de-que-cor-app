package br.com.unhasdequecor.ui.hand

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun HandReferenceMessageEffects(
    message: String?,
    navigateHome: Boolean,
    homeFlashMessage: String?,
    cameraPermissionDenied: Boolean,
    snackbarHostState: SnackbarHostState,
    onMessageConsumed: () -> Unit,
    onNavigateHomeConsumed: (flashMessage: String?) -> Unit,
    onPermissionDeniedConsumed: () -> Unit,
) {
    LaunchedEffect(navigateHome) {
        if (navigateHome) {
            onNavigateHomeConsumed(homeFlashMessage)
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
