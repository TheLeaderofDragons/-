package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.SilhouetteRadarEngine
import java.util.concurrent.Executors

@Composable
fun CameraFeedPreview(
    useFrontLens: Boolean,
    onRgbAndLumaFrame: (rGrid: IntArray, gGrid: IntArray, bGrid: IntArray, lumaGrid: IntArray) -> Unit,
    onFallbackToSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = AppIcons.CameraAlt,
                    contentDescription = "Разрешение камеры",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Требуется доступ к камере для анализа силуэтов и номеров",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Разрешить камеру", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onFallbackToSimulation,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Включить демо-режим")
                    }
                }
            }
        }
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner, useFrontLens) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var frameSkip = 0
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    frameSkip++
                    if (frameSkip % 2 == 0) {
                        extractDownsampledGrids(imageProxy, onRgbAndLumaFrame)
                    }
                    imageProxy.close()
                }

                val selector = if (useFrontLens) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
            } catch (_: Exception) {
                // If device has no camera or binding fails, user can switch to simulation
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

private fun extractDownsampledGrids(
    image: ImageProxy,
    onResult: (rGrid: IntArray, gGrid: IntArray, bGrid: IntArray, lumaGrid: IntArray) -> Unit
) {
    try {
        val planes = image.planes
        if (planes.isEmpty()) return
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        val uPlane = if (planes.size > 1) planes[1] else null
        val vPlane = if (planes.size > 2) planes[2] else null
        val uBuffer = uPlane?.buffer
        val vBuffer = vPlane?.buffer
        val uvRowStride = uPlane?.rowStride ?: 0
        val uvPixelStride = uPlane?.pixelStride ?: 0

        val srcW = image.width
        val srcH = image.height
        val gw = SilhouetteRadarEngine.GRID_W
        val gh = SilhouetteRadarEngine.GRID_H

        val rGrid = IntArray(gw * gh)
        val gGrid = IntArray(gw * gh)
        val bGrid = IntArray(gw * gh)
        val lumaGrid = IntArray(gw * gh)

        for (gy in 0 until gh) {
            val sy = (gy * srcH) / gh
            val yRowOffset = sy * yRowStride
            val uvRowOffset = (sy shr 1) * uvRowStride

            for (gx in 0 until gw) {
                val sx = (gx * srcW) / gw
                val yIdx = yRowOffset + sx * yPixelStride
                val yVal = if (yIdx < yBuffer.limit()) (yBuffer.get(yIdx).toInt() and 0xFF) else 128

                var uVal = 128
                var vVal = 128
                if (uBuffer != null && vBuffer != null) {
                    val uvIdx = uvRowOffset + (sx shr 1) * uvPixelStride
                    if (uvIdx < uBuffer.limit() && uvIdx < vBuffer.limit()) {
                        uVal = uBuffer.get(uvIdx).toInt() and 0xFF
                        vVal = vBuffer.get(uvIdx).toInt() and 0xFF
                    }
                }

                val c = yVal - 16
                val d = uVal - 128
                val e = vVal - 128

                val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                val outIdx = gy * gw + gx
                rGrid[outIdx] = r
                gGrid[outIdx] = g
                bGrid[outIdx] = b
                lumaGrid[outIdx] = yVal
            }
        }

        onResult(rGrid, gGrid, bGrid, lumaGrid)
    } catch (_: Exception) {
    }
}
