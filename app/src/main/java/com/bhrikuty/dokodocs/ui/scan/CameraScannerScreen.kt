package com.bhrikuty.dokodocs.ui.scan

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bhrikuty.dokodocs.core.image.DocumentDetector
import com.bhrikuty.dokodocs.core.image.DocumentQuad
import com.bhrikuty.dokodocs.core.image.ImageProcessor
import com.bhrikuty.dokodocs.theme.AppleBlue
import com.bhrikuty.dokodocs.theme.AppleGreen
import com.bhrikuty.dokodocs.theme.AppleOrange
import com.bhrikuty.dokodocs.theme.PrimaryLight
import com.bhrikuty.dokodocs.viewmodel.ScanViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class DocTypeOption(val id: String, val label: String)

@Composable
fun CameraScannerScreen(
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToCrop: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scannedPages by viewModel.scannedPages.collectAsState()
    val isFlashEnabled by viewModel.isFlashEnabled.collectAsState()
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val isIdBothSidesMode by viewModel.isIdBothSidesMode.collectAsState()
    val idScanStep by viewModel.idScanStep.collectAsState()
    val selectedDocType by viewModel.selectedDocumentType.collectAsState()

    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Live real-time detected quad on the camera preview stream
    var liveDetectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var isDocumentDetected by remember { mutableStateOf(false) }
    var isProcessingCapture by remember { mutableStateOf(false) }

    // Pulsing animation for the detected boundary
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                for (uri in uris) {
                    val bitmap = ImageProcessor.decodeBitmapFromUri(context, uri)
                    if (bitmap != null) {
                        viewModel.addCapturedPage(bitmap)
                    }
                }
                if (!isBatchMode && uris.size == 1) {
                    CoroutineScope(Dispatchers.Main).launch {
                        onNavigateToCrop(scannedPages.size)
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        onNavigateToReview()
                    }
                }
            }
        }
    }

    val docTypes = listOf(
        DocTypeOption("auto", "Auto Detect"),
        DocTypeOption("a4", "A4 Document"),
        DocTypeOption("idCard", "ID / Citizenship"),
        DocTypeOption("receipt", "Receipt / Bill"),
        DocTypeOption("book", "Book Page")
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        // Real-time Frame Analysis for Live Edge Detection
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()

                        var lastAnalysisTime = 0L
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastAnalysisTime >= 200) { // Throttle ~5 fps for low CPU usage
                                lastAnalysisTime = currentTime
                                try {
                                    val bitmap = imageProxy.toBitmap()
                                    CoroutineScope(Dispatchers.Default).launch {
                                        val quad = DocumentDetector.detectDocument(bitmap)
                                        val normQuad = quad.scale(
                                            1f / bitmap.width,
                                            1f / bitmap.height
                                        )
                                        CoroutineScope(Dispatchers.Main).launch {
                                            val current = liveDetectedQuad
                                            if (current != null) {
                                                val alpha = 0.5f
                                                liveDetectedQuad = DocumentQuad(
                                                    topLeft = com.bhrikuty.dokodocs.core.image.Point2D(
                                                        current.topLeft.x * (1 - alpha) + normQuad.topLeft.x * alpha,
                                                        current.topLeft.y * (1 - alpha) + normQuad.topLeft.y * alpha
                                                    ),
                                                    topRight = com.bhrikuty.dokodocs.core.image.Point2D(
                                                        current.topRight.x * (1 - alpha) + normQuad.topRight.x * alpha,
                                                        current.topRight.y * (1 - alpha) + normQuad.topRight.y * alpha
                                                    ),
                                                    bottomRight = com.bhrikuty.dokodocs.core.image.Point2D(
                                                        current.bottomRight.x * (1 - alpha) + normQuad.bottomRight.x * alpha,
                                                        current.bottomRight.y * (1 - alpha) + normQuad.bottomRight.y * alpha
                                                    ),
                                                    bottomLeft = com.bhrikuty.dokodocs.core.image.Point2D(
                                                        current.bottomLeft.x * (1 - alpha) + normQuad.bottomLeft.x * alpha,
                                                        current.bottomLeft.y * (1 - alpha) + normQuad.bottomLeft.y * alpha
                                                    ),
                                                    confidence = normQuad.confidence
                                                )
                                            } else {
                                                liveDetectedQuad = normQuad
                                            }
                                            isDocumentDetected = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    // ignore frame error
                                }
                            }
                            imageProxy.close()
                        }

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis,
                                imageCapture
                            )
                            camera.cameraControl.enableTorch(isFlashEnabled)
                        } catch (e: Exception) {
                            // Camera bind error
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Live Document Boundary Canvas Overlay (CamScanner-Grade Green Contour)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val quad = liveDetectedQuad
            if (quad != null && isDocumentDetected) {
                val pTL = androidx.compose.ui.geometry.Offset(quad.topLeft.x * canvasW, quad.topLeft.y * canvasH)
                val pTR = androidx.compose.ui.geometry.Offset(quad.topRight.x * canvasW, quad.topRight.y * canvasH)
                val pBR = androidx.compose.ui.geometry.Offset(quad.bottomRight.x * canvasW, quad.bottomRight.y * canvasH)
                val pBL = androidx.compose.ui.geometry.Offset(quad.bottomLeft.x * canvasW, quad.bottomLeft.y * canvasH)

                val docPath = Path().apply {
                    moveTo(pTL.x, pTL.y)
                    lineTo(pTR.x, pTR.y)
                    lineTo(pBR.x, pBR.y)
                    lineTo(pBL.x, pBL.y)
                    close()
                }

                // Semi-transparent Green Document Tint
                drawPath(
                    path = docPath,
                    color = AppleGreen.copy(alpha = 0.15f * pulseAlpha)
                )

                // Outer Green Glow & Border
                drawPath(
                    path = docPath,
                    color = AppleGreen.copy(alpha = pulseAlpha),
                    style = Stroke(width = 3.5.dp.toPx())
                )

                // Corner Handles
                listOf(pTL, pTR, pBR, pBL).forEach { pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = AppleGreen,
                        radius = 5.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }

        // Status Pill: "Document Detected / Align Document"
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDocumentDetected) AppleGreen.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isDocumentDetected) "🟢 Document Detected • Hold Steady" else "📄 Place Document in Frame",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Mode Toggle Chips (Single / Batch / ID Both Sides)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isBatchMode) AppleGreen else Color.Black.copy(alpha = 0.5f))
                        .clickable { viewModel.toggleBatchMode() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isBatchMode) "Batch Mode (ON)" else "Batch",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isIdBothSidesMode) AppleOrange else Color.Black.copy(alpha = 0.5f))
                        .clickable { viewModel.toggleIdBothSidesMode() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "ID Both Sides",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Flash Toggle
            IconButton(
                onClick = { viewModel.toggleFlash() },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (isFlashEnabled) Color(0xFFFFD54F) else Color.White
                )
            }
        }

        // Bottom Controls Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(bottom = 28.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Multi-Scan Thumbnail Strip (Displays all captured photos in real-time)
            if (scannedPages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scanned Pages (${scannedPages.size})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to Edit / Reorder",
                            color = PrimaryLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(scannedPages) { index, pageItem ->
                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 74.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.5.dp, PrimaryLight, RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                                    .clickable { onNavigateToCrop(index) }
                            ) {
                                Image(
                                    bitmap = pageItem.bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Page Number Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Delete (x) Button
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.85f))
                                        .clickable { viewModel.removePage(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Document Type Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                items(docTypes) { docType ->
                    val isSelected = selectedDocType == docType.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) PrimaryLight else Color.White.copy(alpha = 0.15f))
                            .clickable { viewModel.setDocumentType(docType.id) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = docType.label,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Shutter & Done Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Import
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isIdBothSidesMode && idScanStep == 1) AppleOrange else PrimaryLight)
                        .clickable(enabled = !isProcessingCapture) {
                            isProcessingCapture = true
                            imageCapture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        val buffer = imageProxy.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        imageProxy.close()

                                        if (bitmap != null) {
                                            val unnormQuad = liveDetectedQuad?.scale(bitmap.width.toFloat(), bitmap.height.toFloat())
                                            viewModel.addCapturedPage(bitmap, unnormQuad)
                                            CoroutineScope(Dispatchers.Main).launch {
                                                isProcessingCapture = false
                                                if (isIdBothSidesMode && scannedPages.size >= 2) {
                                                    onNavigateToReview()
                                                }
                                            }
                                        } else {
                                            isProcessingCapture = false
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isProcessingCapture = false
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessingCapture) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White)
                    }
                }

                // Done / Review Button
                if (scannedPages.isNotEmpty()) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = Color.Red,
                                contentColor = Color.White
                            ) {
                                Text(scannedPages.size.toString())
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onNavigateToReview,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(AppleGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(52.dp))
                }
            }
        }
    }
}
