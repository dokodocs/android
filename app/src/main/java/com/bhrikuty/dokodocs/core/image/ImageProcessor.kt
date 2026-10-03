package com.bhrikuty.dokodocs.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.max

object ImageProcessor {

    suspend fun decodeBitmap(path: String, reqWidth: Int = 0, reqHeight: Int = 0): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (reqWidth > 0 && reqHeight > 0) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(path, options)
                    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeFile(path, options)
                } else {
                    BitmapFactory.decodeFile(path)
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val input: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(input)
                input?.close()
                bitmap
            } catch (e: Exception) {
                null
            }
        }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Corrects perspective of a document. If quad.isCurvedMesh is true, applies nonlinear spline mesh dewarping.
     */
    suspend fun warpPerspective(srcBitmap: Bitmap, quad: DocumentQuad): Bitmap =
        withContext(Dispatchers.Default) {
            if (quad.isCurvedMesh) {
                return@withContext MeshDewarpEngine.dewarpCurvedDocument(srcBitmap, quad, 8, 8)
            }

            val srcPoints = quad.toFloatArray()

            // Calculate output width and height based on the distances between corners
            val widthTop = hypot((quad.topRight.x - quad.topLeft.x).toDouble(), (quad.topRight.y - quad.topLeft.y).toDouble())
            val widthBottom = hypot((quad.bottomRight.x - quad.bottomLeft.x).toDouble(), (quad.bottomRight.y - quad.bottomLeft.y).toDouble())
            val targetWidth = max(widthTop, widthBottom).toInt().coerceIn(100, 4000)

            val heightLeft = hypot((quad.bottomLeft.x - quad.topLeft.x).toDouble(), (quad.bottomLeft.y - quad.topLeft.y).toDouble())
            val heightRight = hypot((quad.bottomRight.x - quad.topRight.x).toDouble(), (quad.bottomRight.y - quad.topRight.y).toDouble())
            val targetHeight = max(heightLeft, heightRight).toInt().coerceIn(100, 4000)

            val dstPoints = floatArrayOf(
                0f, 0f,
                targetWidth.toFloat(), 0f,
                targetWidth.toFloat(), targetHeight.toFloat(),
                0f, targetHeight.toFloat()
            )

            val matrix = Matrix()
            matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

            val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(srcBitmap, matrix, paint)

            outputBitmap
        }

    /**
     * Applies document filters (magic_color, shadow_remove, grayscale, bw, high_contrast, lighten, warm)
     */
    suspend fun applyFilter(srcBitmap: Bitmap, filterName: String): Bitmap =
        withContext(Dispatchers.Default) {
            when (filterName.lowercase()) {
                "shadow_remove", "whiten" -> IlluminationEnhancer.removeShadowsAndWhiten(srcBitmap)
                "grayscale" -> toGrayscale(srcBitmap)
                "bw" -> toBlackAndWhite(srcBitmap)
                "magic_color", "enhance" -> toMagicColor(srcBitmap)
                "high_contrast" -> toHighContrast(srcBitmap)
                "lighten" -> toLighten(srcBitmap)
                "warm" -> toWarm(srcBitmap)
                else -> srcBitmap
            }
        }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun toMagicColor(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        // Magic color: slight saturation boost + contrast bump + brightness lift
        val cm = ColorMatrix(floatArrayOf(
            1.25f, 0f, 0f, 0f, 10f,
            0f, 1.25f, 0f, 0f, 10f,
            0f, 0f, 1.25f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun toHighContrast(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val cm = ColorMatrix(floatArrayOf(
            1.6f, 0f, 0f, 0f, -50f,
            0f, 1.6f, 0f, 0f, -50f,
            0f, 0f, 1.6f, 0f, -50f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun toLighten(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val cm = ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 40f,
            0f, 1.1f, 0f, 0f, 40f,
            0f, 0f, 1.1f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun toWarm(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val cm = ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 20f,
            0f, 1.05f, 0f, 0f, 8f,
            0f, 0f, 0.95f, 0f, -15f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun toBlackAndWhite(src: Bitmap): Bitmap {
        val gray = toGrayscale(src)
        val width = gray.width
        val height = gray.height
        val pixels = IntArray(width * height)
        gray.getPixels(pixels, 0, width, 0, 0, width, height)

        // Compute Otsu / adaptive threshold
        var sum = 0L
        for (p in pixels) {
            sum += (Color.red(p))
        }
        val threshold = (sum / pixels.size).toInt().coerceIn(100, 160)

        for (i in pixels.indices) {
            val v = Color.red(pixels[i])
            pixels[i] = if (v > threshold) Color.WHITE else Color.BLACK
        }

        val bw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bw.setPixels(pixels, 0, width, 0, 0, width, height)
        return bw
    }

    suspend fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap =
        withContext(Dispatchers.Default) {
            if (degrees % 360 == 0f) return@withContext src
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }

    suspend fun saveBitmapToFile(bitmap: Bitmap, destFile: File, quality: Int = 90): String =
        withContext(Dispatchers.IO) {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            destFile.absolutePath
        }

    suspend fun savePngToFile(bitmap: Bitmap, destFile: File): String =
        withContext(Dispatchers.IO) {
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            destFile.absolutePath
        }
}
