package com.example.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.network.PcHost
import com.example.network.QrPairingParser
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.PureSurface
import com.example.ui.theme.SlateInk
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerDialog(
    onHostScanned: (PcHost) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var invalidHint by remember { mutableStateOf(false) }
    val handledOnce = remember { AtomicBoolean(false) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener(
            {
                val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    ?: return@addListener

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || handledOnce.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            if (handledOnce.get()) return@addOnSuccessListener
                            for (barcode in barcodes) {
                                val raw = barcode.rawValue ?: continue
                                val parsed = QrPairingParser.parse(raw)
                                if (parsed != null) {
                                    if (handledOnce.compareAndSet(false, true)) {
                                        mainExecutor.execute {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onHostScanned(parsed)
                                        }
                                    }
                                    break
                                } else {
                                    mainExecutor.execute { invalidHint = true }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                runCatching {
                    cameraProvider.unbindAll()
                    val cam = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                    cameraRef = cam
                    hasFlash = cam.cameraInfo.hasFlashUnit()
                }
            },
            mainExecutor,
        )

        onDispose {
            runCatching {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
            }
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("qr_scanner_dialog"),
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )

            // Scrim + cutout viewfinder + animated cobalt scan line
            val scanTransition = rememberInfiniteTransition(label = "qrScanLine")
            val scanProgress by scanTransition.animateFloat(
                initialValue = 0.08f,
                targetValue = 0.92f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1700),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "scanY",
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
            ) {
                drawRect(Color.Black.copy(alpha = 0.56f))

                val frameSize = minOf(size.width, size.height) * 0.66f
                val left = (size.width - frameSize) / 2f
                val top = (size.height - frameSize) / 2f - 20.dp.toPx()
                val rect = Rect(left, top, left + frameSize, top + frameSize)

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = rect.topLeft,
                    size = Size(frameSize, frameSize),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    blendMode = BlendMode.Clear,
                )

                // Corner accents
                val stroke = 4.dp.toPx()
                val corner = 28.dp.toPx()
                val accent = CobaltAccent

                // Top-left
                drawLine(accent, Offset(rect.left, rect.top + corner), Offset(rect.left, rect.top), stroke, StrokeCap.Round)
                drawLine(accent, Offset(rect.left, rect.top), Offset(rect.left + corner, rect.top), stroke, StrokeCap.Round)
                // Top-right
                drawLine(accent, Offset(rect.right - corner, rect.top), Offset(rect.right, rect.top), stroke, StrokeCap.Round)
                drawLine(accent, Offset(rect.right, rect.top), Offset(rect.right, rect.top + corner), stroke, StrokeCap.Round)
                // Bottom-left
                drawLine(accent, Offset(rect.left, rect.bottom - corner), Offset(rect.left, rect.bottom), stroke, StrokeCap.Round)
                drawLine(accent, Offset(rect.left, rect.bottom), Offset(rect.left + corner, rect.bottom), stroke, StrokeCap.Round)
                // Bottom-right
                drawLine(accent, Offset(rect.right - corner, rect.bottom), Offset(rect.right, rect.bottom), stroke, StrokeCap.Round)
                drawLine(accent, Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - corner), stroke, StrokeCap.Round)

                // Animated scan line
                val lineY = rect.top + frameSize * scanProgress
                drawLine(
                    color = CobaltAccent.copy(alpha = 0.85f),
                    start = Offset(rect.left + 16.dp.toPx(), lineY),
                    end = Offset(rect.right - 16.dp.toPx(), lineY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Top controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = PureSurface.copy(alpha = 0.85f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("qr_close_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_sheet),
                    )
                }

                if (hasFlash) {
                    IconButton(
                        onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            cameraRef?.cameraControl?.enableTorch(next)
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (torchEnabled) CobaltAccent else PureSurface.copy(alpha = 0.85f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("qr_torch_button"),
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = stringResource(R.string.cd_toggle_torch),
                        )
                    }
                }
            }

            // Bottom instructions & invalid QR hint
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = invalidHint,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CoralAlert,
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.qr_invalid_code),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PureSurface.copy(alpha = 0.94f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.qr_scanner_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = SlateInk,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.qr_scanner_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateInk.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
