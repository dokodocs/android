package com.bhrikuty.dokodocs.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.bhrikuty.dokodocs.core.image.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

enum class PageSizeFormat(val displayName: String, val widthPt: Int, val heightPt: Int, val maxSizeBytes: Long = 0) {
    AUTO("Auto", 0, 0),
    A4("A4 (Standard)", 595, 842),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008),
    LOK_SEWA_200KB("Lok Sewa / PSC (< 200 KB)", 595, 842, 200 * 1024),
    PASSPORT_500KB("Nagarik App / MRP (< 500 KB)", 595, 842, 500 * 1024),
    ID_2UP_A4("ID / Citizenship (2-Up on A4)", 595, 842);

    companion object {
        fun fromString(str: String): PageSizeFormat = when (str.lowercase()) {
            "a4" -> A4
            "letter" -> LETTER
            "legal" -> LEGAL
            "lok_sewa_200kb" -> LOK_SEWA_200KB
            "passport_500kb" -> PASSPORT_500KB
            "id_2up_a4" -> ID_2UP_A4
            else -> AUTO
        }
    }
}

data class PdfPageSource(
    val imagePath: String,
    val filter: String = "original",
    val rotation: Float = 0f
)

object PdfGenerator {

    suspend fun generatePdf(
        context: Context,
        pages: List<PdfPageSource>,
        outputFile: File,
        pageSizeFormat: PageSizeFormat = PageSizeFormat.A4,
        includeWatermark: Boolean = false,
        watermarkText: String = "Scanned with DokoDocs • Product of Bhrikuty"
    ): String = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()

        // Handle ID 2-Up on single A4 layout when 2 pages exist
        if (pageSizeFormat == PageSizeFormat.ID_2UP_A4 && pages.size == 2) {
            return@withContext generate2UpIdPdf(
                pages = pages,
                outputFile = outputFile,
                includeWatermark = includeWatermark,
                watermarkText = watermarkText
            )
        }

        val pdfDocument = PdfDocument()

        val watermarkPaint = Paint().apply {
            color = Color.argb(120, 46, 125, 107) // DokoDocs brand teal
            textSize = 14f
            isAntiAlias = true
            isFakeBoldText = true
        }

        for ((index, pageSource) in pages.withIndex()) {
            var rawBitmap = ImageProcessor.decodeBitmap(pageSource.imagePath) ?: continue

            // Apply rotation if needed
            if (pageSource.rotation != 0f) {
                rawBitmap = ImageProcessor.rotateBitmap(rawBitmap, pageSource.rotation)
            }

            // Apply filter if needed
            if (pageSource.filter.isNotEmpty() && pageSource.filter != "original") {
                rawBitmap = ImageProcessor.applyFilter(rawBitmap, pageSource.filter)
            }

            // If compressed preset selected (Lok Sewa / Passport), optimize resolution and compress
            if (pageSizeFormat == PageSizeFormat.LOK_SEWA_200KB) {
                val maxDim = 1200
                if (rawBitmap.width > maxDim || rawBitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(rawBitmap.width, rawBitmap.height)
                    rawBitmap = Bitmap.createScaledBitmap(
                        rawBitmap,
                        (rawBitmap.width * scale).toInt(),
                        (rawBitmap.height * scale).toInt(),
                        true
                    )
                }
            }

            val (pageWidth, pageHeight) = if (pageSizeFormat == PageSizeFormat.AUTO) {
                val w = (rawBitmap.width * 72f / 200f).toInt().coerceIn(200, 2000)
                val h = (rawBitmap.height * 72f / 200f).toInt().coerceIn(200, 2000)
                w to h
            } else {
                pageSizeFormat.widthPt to pageSizeFormat.heightPt
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas = pdfPage.canvas

            // Fit image into page keeping aspect ratio
            val scale = minOf(pageWidth.toFloat() / rawBitmap.width, pageHeight.toFloat() / rawBitmap.height)
            val dstWidth = rawBitmap.width * scale
            val dstHeight = rawBitmap.height * scale
            val left = (pageWidth - dstWidth) / 2f
            val top = (pageHeight - dstHeight) / 2f
            val dstRect = RectF(left, top, left + dstWidth, top + dstHeight)
            val srcRect = Rect(0, 0, rawBitmap.width, rawBitmap.height)

            canvas.drawBitmap(rawBitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

            // Watermark
            if (includeWatermark) {
                val textBounds = Rect()
                watermarkPaint.getTextBounds(watermarkText, 0, watermarkText.length, textBounds)
                val x = pageWidth - textBounds.width() - 24f
                val y = pageHeight - 20f
                canvas.drawText(watermarkText, x, y, watermarkPaint)
            }

            pdfDocument.finishPage(pdfPage)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        outputFile.absolutePath
    }

    /**
     * Special 2-Up A4 layout placing Front and Back ID/Citizenship on a single sheet.
     */
    private suspend fun generate2UpIdPdf(
        pages: List<PdfPageSource>,
        outputFile: File,
        includeWatermark: Boolean,
        watermarkText: String
    ): String {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val pdfPage = pdfDocument.startPage(pageInfo)
        val canvas = pdfPage.canvas

        // Background
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            isAntiAlias = true
        }

        // Draw center divider
        val midY = pageHeight / 2f
        canvas.drawLine(40f, midY, pageWidth - 40f, midY, dividerPaint)

        // Half slots
        val slotH = (pageHeight / 2f) - 60f
        val slotW = pageWidth - 80f

        for (i in 0 until 2) {
            val pageSource = pages[i]
            var bmp = ImageProcessor.decodeBitmap(pageSource.imagePath) ?: continue
            if (pageSource.filter.isNotEmpty() && pageSource.filter != "original") {
                bmp = ImageProcessor.applyFilter(bmp, pageSource.filter)
            }

            val label = if (i == 0) "FRONT SIDE / अगाडिको भाग" else "BACK SIDE / पछाडिको भाग"
            val labelY = if (i == 0) 36f else midY + 36f
            canvas.drawText(label, 40f, labelY, headerPaint)

            val scale = minOf(slotW / bmp.width, slotH / bmp.height)
            val dstW = bmp.width * scale
            val dstH = bmp.height * scale
            val left = (pageWidth - dstW) / 2f
            val top = if (i == 0) 48f + (slotH - dstH) / 2f else midY + 48f + (slotH - dstH) / 2f

            val dstRect = RectF(left, top, left + dstW, top + dstH)
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        if (includeWatermark) {
            val watermarkPaint = Paint().apply {
                color = Color.argb(120, 46, 125, 107)
                textSize = 12f
                isAntiAlias = true
                isFakeBoldText = true
            }
            canvas.drawText(watermarkText, 40f, pageHeight - 20f, watermarkPaint)
        }

        pdfDocument.finishPage(pdfPage)
        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return outputFile.absolutePath
    }

    /**
     * Renders a PDF page to a Bitmap using native PdfRenderer.
     */
    suspend fun renderPdfPage(pdfFile: File, pageIndex: Int = 0, targetWidth: Int = 800): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                if (pageIndex >= renderer.pageCount) {
                    renderer.close()
                    pfd.close()
                    return@withContext null
                }
                val page = renderer.openPage(pageIndex)
                val aspectRatio = page.height.toFloat() / page.width.toFloat()
                val targetHeight = (targetWidth * aspectRatio).toInt()

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                page.close()
                renderer.close()
                pfd.close()
                bitmap
            } catch (e: Exception) {
                null
            }
        }
}
