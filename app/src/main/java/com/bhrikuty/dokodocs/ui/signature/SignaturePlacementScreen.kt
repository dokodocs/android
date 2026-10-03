package com.bhrikuty.dokodocs.ui.signature

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhrikuty.dokodocs.core.image.ImageProcessor
import com.bhrikuty.dokodocs.core.pdf.PdfGenerator
import com.bhrikuty.dokodocs.core.pdf.PdfPageSource
import com.bhrikuty.dokodocs.data.model.Document
import com.bhrikuty.dokodocs.data.model.Page
import com.bhrikuty.dokodocs.data.model.Signature
import com.bhrikuty.dokodocs.theme.PrimaryLight
import com.bhrikuty.dokodocs.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePlacementScreen(
    documentId: Long,
    pageIndex: Int = 0,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onSignatureApplied: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val signatures by viewModel.signatureRepo.allSignatures.collectAsState(initial = emptyList())

    var document by remember { mutableStateOf<Document?>(null) }
    var pageEntity by remember { mutableStateOf<Page?>(null) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedSignature by remember { mutableStateOf<Signature?>(null) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var sigOffset by remember { mutableStateOf(Offset(100f, 200f)) }
    var sigScale by remember { mutableFloatStateOf(1.0f) }
    var isApplying by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        withContext(Dispatchers.IO) {
            val doc = viewModel.documentRepo.getDocumentById(documentId)
            document = doc
            val pages = viewModel.documentRepo.getPagesForDocumentSync(documentId)
            val p = pages.getOrNull(pageIndex) ?: pages.firstOrNull()
            pageEntity = p
            if (p != null) {
                pageBitmap = ImageProcessor.decodeBitmap(p.localImagePath)
            }
        }
    }

    LaunchedEffect(selectedSignature) {
        if (selectedSignature != null) {
            withContext(Dispatchers.IO) {
                val file = File(selectedSignature!!.imagePath)
                if (file.exists()) {
                    signatureBitmap = BitmapFactory.decodeFile(file.absolutePath)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Place Signature on Document", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (signatureBitmap != null && pageBitmap != null && pageEntity != null && document != null) {
                        Button(
                            enabled = !isApplying,
                            onClick = {
                                isApplying = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val bmp = pageBitmap!!
                                    val sig = signatureBitmap!!
                                    val p = pageEntity!!
                                    val doc = document!!

                                    // Burn signature onto mutable page bitmap
                                    val resultBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                                    val canvas = Canvas(resultBitmap)

                                    val scaleX = bmp.width.toFloat() / containerSize.width.toFloat()
                                    val scaleY = bmp.height.toFloat() / containerSize.height.toFloat()

                                    val finalSigW = (sig.width * 0.4f * sigScale) * scaleX
                                    val finalSigH = (sig.height * 0.4f * sigScale) * scaleY

                                    val finalX = sigOffset.x * scaleX
                                    val finalY = sigOffset.y * scaleY

                                    val dstRect = RectF(finalX, finalY, finalX + finalSigW, finalY + finalSigH)
                                    canvas.drawBitmap(sig, null, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

                                    // Overwrite processed page image
                                    ImageProcessor.saveBitmapToFile(resultBitmap, File(p.localImagePath))

                                    // Regenerate PDF
                                    val allPages = viewModel.documentRepo.getPagesForDocumentSync(documentId)
                                    val pdfSources = allPages.map {
                                        PdfPageSource(imagePath = it.localImagePath, filter = it.filter, rotation = it.rotation.toFloat())
                                    }
                                    PdfGenerator.generatePdf(
                                        context = context,
                                        pages = pdfSources,
                                        outputFile = File(doc.localPdfPath)
                                    )

                                    withContext(Dispatchers.Main) {
                                        isApplying = false
                                        onSignatureApplied()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (isApplying) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apply", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Interactive Page View with Signature Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.DarkGray)
                    .onGloballyPositioned { containerSize = it.size }
            ) {
                if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "Document Page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryLight)
                }

                // Draggable Signature Overlay Box
                if (signatureBitmap != null) {
                    val density = LocalDensity.current
                    val baseSigWidth = 140.dp * sigScale
                    val baseSigHeight = 70.dp * sigScale

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(sigOffset.x.roundToInt(), sigOffset.y.roundToInt()) }
                            .size(baseSigWidth, baseSigHeight)
                            .border(1.5.dp, PrimaryLight, RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    sigOffset = Offset(
                                        (sigOffset.x + dragAmount.x).coerceIn(0f, containerSize.width.toFloat() - 100f),
                                        (sigOffset.y + dragAmount.y).coerceIn(0f, containerSize.height.toFloat() - 60f)
                                    )
                                }
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = signatureBitmap!!.asImageBitmap(),
                            contentDescription = "Signature",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom Signature Selector Panel
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose Signature to Stamp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (signatures.isEmpty()) {
                        Text(
                            text = "No saved signatures found. Please create one in Tools > Sign Document first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(signatures) { sig ->
                                val isSelected = selectedSignature?.id == sig.id
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) PrimaryLight.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier
                                        .size(110.dp, 64.dp)
                                        .border(
                                            if (isSelected) 2.dp else 0.dp,
                                            if (isSelected) PrimaryLight else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedSignature = sig }
                                        .padding(6.dp)
                                ) {
                                    val file = File(sig.imagePath)
                                    if (file.exists()) {
                                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = sig.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedSignature != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size: ", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = sigScale,
                                    onValueChange = { sigScale = it },
                                    valueRange = 0.5f..2.2f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
