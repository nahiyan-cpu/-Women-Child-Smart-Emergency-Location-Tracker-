package com.example.ui.screens

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

@Composable
fun CameraCaptureModal(
    onCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().apply {
                                surfaceProvider = previewView.surfaceProvider
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                        } catch (e: Exception) {
                            onError(e.localizedMessage ?: "Camera unavailable")
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )

            DisposableEffect(Unit) {
                onDispose {
                    runCatching { cameraProviderFuture.get().unbindAll() }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp, start = 16.dp, end = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Visible Consented Camera", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("Captured photo will be encrypted with AES-256-GCM.", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Camera", tint = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(72.dp),
                ) {
                    IconButton(
                        onClick = {
                            val capture = imageCapture ?: return@IconButton
                            val cacheDir = File(context.cacheDir, "camera_cache").apply { mkdirs() }
                            val photoFile = File(cacheDir, "ev_${UUID.randomUUID()}.jpg")
                            val outputOpts = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            capture.takePicture(
                                outputOpts,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        onCaptured(Uri.fromFile(photoFile))
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        onError(exception.localizedMessage ?: "Capture failed")
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Capture Evidence Photo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}
