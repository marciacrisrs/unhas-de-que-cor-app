package br.com.unhasdequecor.ui.tryon

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun LiveTryOnScreen(onBack: () -> Unit, viewModel: LiveTryOnViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (granted) LiveCameraContent(viewModel.pipeline, onBack)
        else PermissionContent(onBack) { launcher.launch(Manifest.permission.CAMERA) }
    }
}

@Composable
private fun PermissionContent(onBack: () -> Unit, onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White)
            Text("Precisamos da câmera para o try-on ao vivo.", color = Color.White)
            Button(onClick = onRequest) { Text("Permitir câmera") }
            TextButton(onClick = onBack) { Text("Voltar") }
        }
    }
}

@Composable
private fun LiveCameraContent(pipeline: NailTryOnPipeline, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val processing = remember { AtomicBoolean(false) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val analyzer = LiveFrameAnalyzer(pipeline, processing) { bitmap -> processedBitmap = bitmap }
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdownNow()
            processedBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        processedBitmap?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Prévia do try-on ao vivo",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
                Column {
                    Text("Try-on ao vivo", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("Mova sua mão para testar o acompanhamento.", color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            color = Color.Black.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Try-on ativo", color = Color.White)
            }
        }
    }
}

private class LiveFrameAnalyzer(
    private val pipeline: NailTryOnPipeline,
    private val processing: AtomicBoolean,
    private val onResult: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            image.close()
            return
        }
        try {
            val bitmap = imageProxyToBitmap(image) ?: return
            val oriented = rotateAndMirror(bitmap, image.imageInfo.rotationDegrees)
            if (oriented !== bitmap && !bitmap.isRecycled) bitmap.recycle()
            val result = pipeline.process(oriented, Color(0xFFC04A67), stabilize = true)
            result?.bitmap?.let { painted ->
                if (painted !== oriented && !oriented.isRecycled) oriented.recycle()
                onResult(painted)
            } ?: run { if (!oriented.isRecycled) oriented.recycle() }
        } finally {
            image.close()
            processing.set(false)
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) return null
    val y = image.planes[0].buffer
    val u = image.planes[1].buffer
    val v = image.planes[2].buffer
    val yBytes = ByteArray(y.remaining()).also { y.get(it) }
    val uBytes = ByteArray(u.remaining()).also { u.get(it) }
    val vBytes = ByteArray(v.remaining()).also { v.get(it) }
    val nv21 = ByteArray(yBytes.size + uBytes.size + vBytes.size)
    yBytes.copyInto(nv21)
    var offset = yBytes.size
    var i = 0
    while (i < vBytes.size) {
        nv21[offset++] = vBytes[i]
        if (i < uBytes.size) nv21[offset++] = uBytes[i]
        i++
    }
    val output = ByteArrayOutputStream()
    val success = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        .compressToJpeg(Rect(0, 0, image.width, image.height), 85, output)
    return if (success) BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size()) else null
}

private fun rotateAndMirror(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    val matrix = Matrix().apply {
        if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
