package com.bhrikuty.dokodocs.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhrikuty.dokodocs.core.image.DetectionConfidence
import com.bhrikuty.dokodocs.core.image.DocumentQuad
import com.bhrikuty.dokodocs.core.image.PaperStandard
import com.bhrikuty.dokodocs.core.image.Point2D
import com.bhrikuty.dokodocs.theme.PrimaryLight
import com.bhrikuty.dokodocs.viewmodel.ScanViewModel
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun CropEditorScreen(
    pageIndex: Int,
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    val scannedPages by viewModel.scannedPages.collectAsState()
    val pageItem = scannedPages.getOrNull(pageIndex)

    if (pageItem == null) {
        onNavigateToReview()
        return
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var quad by remember {
        mutableStateOf(
            pageItem.quad ?: DocumentQuad.defaultFromSize(pageItem.bitmap.width.toFloat(), pageItem.bitmap.height.toFloat())
        )
    }

    var activeCorner by remember { mutableStateOf<Int?>(null) } // 0: TL, 1: TR, 2: BR, 3: BL
    var dragTouchPoint by remember { mutableStateOf(Offset.Zero) }

    val detectedPaper = remember(quad) { quad.detectPaperStandard() }

    val boundaryColor = when (quad.confidence) {
        DetectionConfidence.HIGH -> Color(0xFF00E676) // Green for confident
        DetectionConfidence.ESTIMATED -> Color(0xFFFFD600) // Yellow for inferred/fold
        DetectionConfidence.MANUAL -> Color(0xFF00E5FF) // Cyan for manual adjust
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Smart Geometry & Dewarp",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = detectedPaper.displayName,
                        color = boundaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Reset to Full
                IconButton(
                    onClick = {
                        quad = DocumentQuad.defaultFromSize(pageItem.bitmap.width.toFloat(), pageItem.bitmap.height.toFloat())
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Toolbar: Dewarp Curved Paper Toggle & Infer Missing Corner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = quad.isCurvedMesh,
                    onClick = {
                        quad = quad.copy(isCurvedMesh = !quad.isCurvedMesh)
                    },
                    label = {
                        Text(
                            text = if (quad.isCurvedMesh) "Mesh Dewarp ON" else "Dewarp Curved",
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.GridOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (quad.isCurvedMesh) Color.White else Color.Gray
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryLight,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.15f),
                        labelColor = Color.White
                    )
                )

                FilterChip(
                    selected = false,
                    onClick = {
                        // Reconstruct corner closest to bottom-right or active
                        val targetCorner = activeCorner ?: 2
                        quad = quad.inferMissingCorner(targetCorner)
                    },
                    label = {
                        Text("Fix Folded Corner", fontSize = 11.sp)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFFD600)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        labelColor = Color.White
                    )
                )
            }
        }

        // Image Canvas with interactive quadrilateral & Mesh Grid Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 130.dp)
                .onGloballyPositioned { canvasSize = it.size }
        ) {
            if (canvasSize.width > 0 && canvasSize.height > 0) {
                val scaleX = canvasSize.width.toFloat() / pageItem.bitmap.width.toFloat()
                val scaleY = canvasSize.height.toFloat() / pageItem.bitmap.height.toFloat()
                val scale = minOf(scaleX, scaleY)

                val displayW = pageItem.bitmap.width * scale
                val displayH = pageItem.bitmap.height * scale
                val offsetX = (canvasSize.width - displayW) / 2f
                val offsetY = (canvasSize.height - displayH) / 2f

                // Render Bitmap
                androidx.compose.foundation.Image(
                    bitmap = pageItem.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size((displayW / LocalDensity.current.density).dp, (displayH / LocalDensity.current.density).dp)
                        .align(Alignment.Center)
                )

                // Draggable Handles Overlay
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val pts = listOf(
                                        Offset(offsetX + quad.topLeft.x * scale, offsetY + quad.topLeft.y * scale),
                                        Offset(offsetX + quad.topRight.x * scale, offsetY + quad.topRight.y * scale),
                                        Offset(offsetX + quad.bottomRight.x * scale, offsetY + quad.bottomRight.y * scale),
                                        Offset(offsetX + quad.bottomLeft.x * scale, offsetY + quad.bottomLeft.y * scale)
                                    )
                                    val hitIndex = pts.indexOfFirst { pt ->
                                        hypot((pt.x - offset.x).toDouble(), (pt.y - offset.y).toDouble()) < 75f
                                    }
                                    activeCorner = if (hitIndex >= 0) hitIndex else null
                                    dragTouchPoint = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val corner = activeCorner ?: return@detectDragGestures
                                    val dx = dragAmount.x / scale
                                    val dy = dragAmount.y / scale
                                    dragTouchPoint = change.position

                                    val updated = when (corner) {
                                        0 -> quad.copy(topLeft = Point2D(quad.topLeft.x + dx, quad.topLeft.y + dy))
                                        1 -> quad.copy(topRight = Point2D(quad.topRight.x + dx, quad.topRight.y + dy))
                                        2 -> quad.copy(bottomRight = Point2D(quad.bottomRight.x + dx, quad.bottomRight.y + dy))
                                        3 -> quad.copy(bottomLeft = Point2D(quad.bottomLeft.x + dx, quad.bottomLeft.y + dy))
                                        else -> quad
                                    }
                                    quad = updated.copy(confidence = DetectionConfidence.MANUAL)
                                },
                                onDragEnd = { activeCorner = null }
                            )
                        }
                ) {
                    val tl = Offset(offsetX + quad.topLeft.x * scale, offsetY + quad.topLeft.y * scale)
                    val tr = Offset(offsetX + quad.topRight.x * scale, offsetY + quad.topRight.y * scale)
                    val br = Offset(offsetX + quad.bottomRight.x * scale, offsetY + quad.bottomRight.y * scale)
                    val bl = Offset(offsetX + quad.bottomLeft.x * scale, offsetY + quad.bottomLeft.y * scale)

                    // Draw Mesh Lines if Curved Mesh Mode is active
                    if (quad.isCurvedMesh) {
                        val cols = 4
                        val rows = 4
                        for (r in 1 until rows) {
                            val v = r.toFloat() / rows
                            val pStart = Offset((1 - v) * tl.x + v * bl.x, (1 - v) * tl.y + v * bl.y)
                            val pEnd = Offset((1 - v) * tr.x + v * br.x, (1 - v) * tr.y + v * br.y)
                            drawLine(color = boundaryColor.copy(alpha = 0.4f), start = pStart, end = pEnd, strokeWidth = 1.dp.toPx())
                        }
                        for (c in 1 until cols) {
                            val u = c.toFloat() / cols
                            val pStart = Offset((1 - u) * tl.x + u * tr.x, (1 - u) * tl.y + u * tr.y)
                            val pEnd = Offset((1 - u) * bl.x + u * br.x, (1 - u) * bl.y + u * br.y)
                            drawLine(color = boundaryColor.copy(alpha = 0.4f), start = pStart, end = pEnd, strokeWidth = 1.dp.toPx())
                        }
                    }

                    // Draw Main Quad Outline
                    val path = Path().apply {
                        moveTo(tl.x, tl.y)
                        lineTo(tr.x, tr.y)
                        lineTo(br.x, br.y)
                        lineTo(bl.x, bl.y)
                        close()
                    }
                    drawPath(path, color = boundaryColor, style = Stroke(width = 3.dp.toPx()))

                    // Draw Corner Circles with color coding
                    listOf(tl, tr, br, bl).forEachIndexed { i, pt ->
                        val isCurrent = activeCorner == i
                        drawCircle(color = Color.White, radius = if (isCurrent) 14.dp.toPx() else 10.dp.toPx(), center = pt)
                        drawCircle(color = boundaryColor, radius = if (isCurrent) 10.dp.toPx() else 7.dp.toPx(), center = pt)
                    }
                }

                // Loupe / Magnifier Preview above active touch point
                if (activeCorner != null) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (dragTouchPoint.x - 45.dp.toPx()).roundToInt().coerceIn(0, canvasSize.width - 90),
                                    (dragTouchPoint.y - 110.dp.toPx()).roundToInt().coerceAtLeast(0)
                                )
                            }
                            .size(90.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, boundaryColor, CircleShape)
                            .background(Color.DarkGray)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = pageItem.bitmap.asImageBitmap(),
                            contentDescription = "Magnifier",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(boundaryColor)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    viewModel.updatePageQuad(pageIndex, quad)
                    onNavigateToReview()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (quad.isCurvedMesh) "Apply Mesh Dewarp" else "Apply Perspective Crop",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
