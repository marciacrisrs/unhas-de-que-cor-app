package br.com.unhasdequecor.ui.tryon

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.data.vision.nail.TryOnPreviewLabels
import br.com.unhasdequecor.ui.components.ErrorContent
import br.com.unhasdequecor.ui.components.NailDebugOverlay
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.SecondaryCtaButton
import java.util.concurrent.Executors

@Composable
fun LiveTryOnScreen(
    onBack: () -> Unit,
    viewModel: LiveTryOnViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    KeepScreenOn()
    if (state.errorMessage != null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            ErrorContent(
                message = state.errorMessage.orEmpty(),
                onRetry = onBack,
                retryLabel = "Voltar",
            )
        }
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        LiveTryOnPermissionGate(
            state = state,
            onBack = onBack,
            onFrame = viewModel::consumeFrame,
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun LiveTryOnPermissionGate(
    state: LiveTryOnUiState,
    onBack: () -> Unit,
    onFrame: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val cameraAvailable = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    LaunchedEffect(cameraAvailable) {
        if (cameraAvailable && !granted) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    when {
        !cameraAvailable -> PermissionPane(
            title = "Este aparelho não tem câmera.",
            actionLabel = "Voltar",
            onAction = onBack,
            onBack = onBack,
        )
        granted -> LiveCameraPane(
            state = state,
            onBack = onBack,
            onFrame = onFrame,
        )
        else -> PermissionPane(
            title = "Precisamos da câmera para o try-on ao vivo.",
            actionLabel = "Permitir câmera",
            onAction = { launcher.launch(Manifest.permission.CAMERA) },
            onBack = onBack,
        )
    }
}

@Composable
private fun PermissionPane(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            PrimaryCtaButton(text = actionLabel, onClick = onAction)
            SecondaryCtaButton(text = "Voltar", onClick = onBack)
        }
    }
}

@Composable
private fun LiveCameraPane(
    state: LiveTryOnUiState,
    onBack: () -> Unit,
    onFrame: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val statusText = TryOnPreviewLabels.status(state.claim, state.failureReason)
    val frameDescription = TryOnPreviewLabels.contentDescription(
        colorName = state.colorName,
        claim = state.claim,
        reason = state.failureReason,
    )

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { useCase ->
                    useCase.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(executor, LiveFrameAnalyzer(onFrame))
                    }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdownNow()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = frameDescription
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        val overlay = state.overlay
        if (overlay != null && !overlay.isRecycled) {
            Image(
                bitmap = overlay.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.showDebug) {
            NailDebugOverlay(
                landmarks = state.landmarks,
                nails = state.nails,
                modifier = Modifier.fillMaxSize(),
            )
        }
        LiveTryOnChrome(
            colorName = state.colorName,
            statusText = statusText,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun LiveTryOnChrome(
    colorName: String,
    statusText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = CHROME_SCRIM_ALPHA))
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Try-on ao vivo",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = colorName,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            text = statusText,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

private class LiveFrameAnalyzer(
    private val onFrame: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            val raw = image.toBitmap()
            val oriented = orientFrontCamera(raw, image.imageInfo.rotationDegrees)
            if (oriented !== raw && !raw.isRecycled) {
                raw.recycle()
            }
            onFrame(oriented)
        } finally {
            image.close()
        }
    }
}

internal fun orientFrontCamera(source: Bitmap, rotationDegrees: Int): Bitmap {
    val matrix = Matrix()
    if (rotationDegrees != 0) {
        matrix.postRotate(rotationDegrees.toFloat())
    }
    matrix.postScale(-1f, 1f)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
}

private const val CHROME_SCRIM_ALPHA = 0.45f
