package com.bhrikuty.dokodocs.ui.scan

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhrikuty.dokodocs.core.image.ImageProcessor
import com.bhrikuty.dokodocs.core.pdf.PageSizeFormat
import com.bhrikuty.dokodocs.core.redact.RedactionProcessor
import com.bhrikuty.dokodocs.core.redact.RedactionRect
import com.bhrikuty.dokodocs.theme.AppleBlue
import com.bhrikuty.dokodocs.theme.AppleGreen
import com.bhrikuty.dokodocs.theme.AppleOrange
import com.bhrikuty.dokodocs.theme.ApplePurple
import com.bhrikuty.dokodocs.theme.PrimaryLight
import com.bhrikuty.dokodocs.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FilterOption(val id: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCrop: (Int) -> Unit,
    onNavigateToCamera: () -> Unit,
    onDocumentSaved: (Long) -> Unit
) {
    val scannedPages by viewModel.scannedPages.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    if (scannedPages.isEmpty()) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { scannedPages.size })
    val currentPageIndex = pagerState.currentPage.coerceIn(0, scannedPages.size - 1)
    val currentPage = scannedPages[currentPageIndex]

    // Rendered preview cache for current page
    var previewBitmap by remember(currentPageIndex, currentPage.filter, currentPage.quad, currentPage.redactions.size) {
        mutableStateOf<Bitmap?>(null)
    }

    var isRedactionMode by remember { mutableStateOf(false) }

    LaunchedEffect(currentPageIndex, currentPage.filter, currentPage.quad, currentPage.redactions.size) {
        withContext(Dispatchers.Default) {
            val warped = if (currentPage.quad != null) {
                ImageProcessor.warpPerspective(currentPage.bitmap, currentPage.quad!!)
            } else {
                currentPage.bitmap
            }
            val filtered = ImageProcessor.applyFilter(warped, currentPage.filter)
            val redacted = if (currentPage.redactions.isNotEmpty()) {
                RedactionProcessor.applyRedactions(filtered, currentPage.redactions)
            } else {
                filtered
            }
            previewBitmap = redacted
        }
    }

    val filters = listOf(
        FilterOption("magic_color", "✨ Magic Color (Auto)"),
        FilterOption("bw", "🖨️ Crisp B&W Scan"),
        FilterOption("shadow_remove", "📄 Whiten & Clean"),
        FilterOption("high_contrast", "⚡ High Contrast"),
        FilterOption("grayscale", "🩶 Grayscale"),
        FilterOption("original", "📷 Original Photo"),
        FilterOption("lighten", "☀️ Lighten"),
        FilterOption("warm", "🕯️ Warm")
    )

    var showSaveDialog by remember { mutableStateOf(false) }
    var docTitle by remember {
        val categoryPrefix = currentPage.classification?.category?.nameEn ?: "Scan"
        mutableStateOf("$categoryPrefix ${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}")
    }
    var selectedPageFormat by remember {
        val defaultFormat = if (scannedPages.size == 2 && currentPage.classification?.isDoubleSidedExpected == true) {
            PageSizeFormat.ID_2UP_A4
        } else {
            PageSizeFormat.A4
        }
        mutableStateOf(defaultFormat)
    }
    var isSaving by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            title = {
                Text(
                    "Save PDF Document",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("Document Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Preset / Format (सरकारी फाराम / साइज)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val presets = listOf(
                        PageSizeFormat.A4,
                        PageSizeFormat.LOK_SEWA_200KB,
                        PageSizeFormat.PASSPORT_500KB,
                        PageSizeFormat.ID_2UP_A4,
                        PageSizeFormat.AUTO
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { format ->
                            val isSelected = selectedPageFormat == format
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) AppleBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPageFormat = format }
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) AppleBlue else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = format.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AppleBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSaving,
                    onClick = {
                        isSaving = true
                        viewModel.saveDocument(
                            title = docTitle,
                            pageSizeFormat = selectedPageFormat,
                            onSuccess = { docId ->
                                isSaving = false
                                showSaveDialog = false
                                onDocumentSaved(docId)
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Export PDF", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isSaving) {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Page ${currentPageIndex + 1} of ${scannedPages.size}" + (currentPage.sideLabel?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        currentPage.classification?.let {
                            Text(
                                text = "${it.category.nameEn} • ${it.category.nameNe}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showSaveDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PDF", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quality Report Card Banner
            currentPage.qualityReport?.let { report ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (report.grade) {
                            "A" -> AppleGreen.copy(alpha = 0.12f)
                            "B" -> AppleBlue.copy(alpha = 0.12f)
                            "C" -> AppleOrange.copy(alpha = 0.15f)
                            else -> Color(0xFFFFEBEE)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (report.score >= 75) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (report.score >= 75) AppleGreen else AppleOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scan Quality: ${report.score}/100 (Grade ${report.grade})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (report.score >= 75) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                            )
                            if (report.issues.isNotEmpty()) {
                                Text(
                                    text = report.issues.first().messageEn,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Preview View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .pointerInput(isRedactionMode) {
                                if (isRedactionMode) {
                                    detectTapGestures { offset ->
                                        val normX = (offset.x / size.width).coerceIn(0.1f, 0.9f)
                                        val normY = (offset.y / size.height).coerceIn(0.1f, 0.9f)
                                        val rect = android.graphics.RectF(
                                            normX - 0.15f,
                                            normY - 0.05f,
                                            normX + 0.15f,
                                            normY + 0.05f
                                        )
                                        viewModel.addRedaction(currentPageIndex, RedactionRect(rect))
                                    }
                                }
                            }
                    )
                } else {
                    CircularProgressIndicator(color = PrimaryLight)
                }
            }

            // Controls & Filters Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Multi-Page Thumbnails Navigation Strip (When multiple pages exist)
                if (scannedPages.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        itemsIndexed(scannedPages) { index, item ->
                            val isSelected = currentPageIndex == index
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 58.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.5.dp,
                                        color = if (isSelected) PrimaryLight else Color.LightGray,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = item.bitmap.asImageBitmap(),
                                    contentDescription = "Thumb ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(if (isSelected) PrimaryLight else Color.Black.copy(alpha = 0.6f))
                                        .padding(vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons Row (Crop, Retake, Redact, Delete, Add Page)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(
                        onClick = { onNavigateToCrop(currentPageIndex) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = "Crop", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Redact Privacy Masking Toggle
                    IconButton(
                        onClick = { isRedactionMode = !isRedactionMode },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isRedactionMode) ApplePurple else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Redact",
                            tint = if (isRedactionMode) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onNavigateToCamera,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Page", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = {
                            viewModel.removePage(currentPageIndex)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = MaterialTheme.colorScheme.error)
                    }
                }

                if (isRedactionMode) {
                    Text(
                        text = "🔒 Redaction Mode: Tap on document to mask sensitive numbers/photos",
                        fontSize = 11.sp,
                        color = ApplePurple,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Choices
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(filters) { filter ->
                        val isSelected = currentPage.filter == filter.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.updatePageFilter(currentPageIndex, filter.id)
                            },
                            label = { Text(filter.name, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}
