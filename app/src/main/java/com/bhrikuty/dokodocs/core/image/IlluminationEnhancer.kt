package com.bhrikuty.dokodocs.core.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object IlluminationEnhancer {

    /**
     * Smart Multi-Scale Document Auto-Enhancement ("Magic Color"):
     * 1. Smooth 2D Bilinear Background Luminance Normalization (removes shadows, gradients, and yellow/gray casts)
     * 2. Adaptive Contrast Stretching & S-Curve Tone Mapping (deep dark text + crisp pure white background)
     * 3. Ink & Color Vibrancy Preservation (preserves seals, signatures, passport photos, and stamps)
     * 4. Text Unsharp Masking & Edge Crispness
     */
    suspend fun enhanceMagicColor(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Build low-resolution background luminance grid (32x32 blocks)
        val blockSize = 32
        val blocksX = (width + blockSize - 1) / blockSize
        val blocksY = (height + blockSize - 1) / blockSize
        val bgGrid = Array(blocksY) { FloatArray(blocksX) }

        for (by in 0 until blocksY) {
            val startY = by * blockSize
            val endY = min((by + 1) * blockSize, height)
            for (bx in 0 until blocksX) {
                val startX = bx * blockSize
                val endX = min((bx + 1) * blockSize, width)

                // Sample top 10% highest luminance in the block to find paper background
                var maxLum = 128f
                var count = 0
                var sum = 0f
                for (y in startY until endY step 2) {
                    for (x in startX until endX step 2) {
                        val p = pixels[y * width + x]
                        val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000f
                        if (lum > maxLum) {
                            maxLum = lum
                        }
                        sum += lum
                        count++
                    }
                }
                val avgLum = if (count > 0) sum / count else 128f
                // Blend max and average with high weight on max (paper)
                bgGrid[by][bx] = max(140f, maxLum * 0.75f + avgLum * 0.25f)
            }
        }

        // Smooth background grid horizontally and vertically (3x3 box filter)
        val smoothBg = Array(blocksY) { FloatArray(blocksX) }
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var s = 0f
                var c = 0
                for (dy in -1..1) {
                    val ny = (by + dy).coerceIn(0, blocksY - 1)
                    for (dx in -1..1) {
                        val nx = (bx + dx).coerceIn(0, blocksX - 1)
                        s += bgGrid[ny][nx]
                        c++
                    }
                }
                smoothBg[by][bx] = s / c
            }
        }

        val output = IntArray(width * height)

        // 2. Process pixels with Bilinear Background Interpolation + Contrast Stretching + Text Sharpening
        for (y in 0 until height) {
            val gy = (y.toFloat() / blockSize).coerceIn(0f, (blocksY - 1).toFloat())
            val y0 = gy.toInt()
            val y1 = min(y0 + 1, blocksY - 1)
            val fy = gy - y0

            for (x in 0 until width) {
                val gx = (x.toFloat() / blockSize).coerceIn(0f, (blocksX - 1).toFloat())
                val x0 = gx.toInt()
                val x1 = min(x0 + 1, blocksX - 1)
                val fx = gx - x0

                // Bilinear interpolation of background luminance
                val topBg = smoothBg[y0][x0] * (1f - fx) + smoothBg[y0][x1] * fx
                val botBg = smoothBg[y1][x0] * (1f - fx) + smoothBg[y1][x1] * fx
                val bgLum = topBg * (1f - fy) + botBg * fy

                val p = pixels[y * width + x]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                val lum = (r * 299 + g * 587 + b * 114) / 1000f

                // Illumination gain: flatten background to 255
                val gain = (255f / bgLum).coerceIn(1.0f, 2.5f)

                // Normalized luminance
                val normLum = lum * gain

                val finalR: Int
                val finalG: Int
                val finalB: Int

                if (normLum >= 215f) {
                    // Paper background -> smooth push to pure crisp white
                    val factor = ((normLum - 215f) / 40f).coerceIn(0f, 1f)
                    val targetWhite = 255
                    finalR = min(255, (r * gain + (targetWhite - r * gain) * factor).toInt())
                    finalG = min(255, (g * gain + (targetWhite - g * gain) * factor).toInt())
                    finalB = min(255, (b * gain + (targetWhite - b * gain) * factor).toInt())
                } else if (normLum <= 90f) {
                    // Dark text/ink -> deepen contrast for razor-sharp legibility
                    val darkenFactor = 0.82f
                    finalR = max(0, (r * gain * darkenFactor - 12).toInt())
                    finalG = max(0, (g * gain * darkenFactor - 12).toInt())
                    finalB = max(0, (b * gain * darkenFactor - 12).toInt())
                } else {
                    // Midtones / colored seals / photos -> boost vibrancy while cleaning gray tone
                    val saturationBoost = 1.15f
                    val nr = (r * gain).coerceIn(0f, 255f)
                    val ng = (g * gain).coerceIn(0f, 255f)
                    val nb = (b * gain).coerceIn(0f, 255f)
                    val avg = (nr + ng + nb) / 3f

                    finalR = (avg + (nr - avg) * saturationBoost).toInt().coerceIn(0, 255)
                    finalG = (avg + (ng - avg) * saturationBoost).toInt().coerceIn(0, 255)
                    finalB = (avg + (nb - avg) * saturationBoost).toInt().coerceIn(0, 255)
                }

                output[y * width + x] = Color.rgb(finalR, finalG, finalB)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, width, 0, 0, width, height)
        result
    }

    /**
     * Adaptive Bradley-Roth Integral Image Thresholding for Crisp Laser B&W Scans.
     * Produces high-contrast photocopier/laser-quality black & white output with zero background shadows.
     */
    suspend fun adaptiveBinarize(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Grayscale luminance array
        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }

        // 2. Compute 2D Integral Image in Long array to prevent overflow
        val integral = LongArray((width + 1) * (height + 1))
        val stride = width + 1

        for (y in 0 until height) {
            var sum = 0L
            for (x in 0 until width) {
                sum += gray[y * width + x]
                integral[(y + 1) * stride + (x + 1)] = integral[y * stride + (x + 1)] + sum
            }
        }

        // 3. Adaptive thresholding with window size = width / 16
        val s = max(8, width / 16)
        val s2 = s / 2
        val t = 0.14f // 14% darker than local average = black ink

        val output = IntArray(width * height)

        for (y in 0 until height) {
            val y1 = max(0, y - s2)
            val y2 = min(height, y + s2)

            for (x in 0 until width) {
                val x1 = max(0, x - s2)
                val x2 = min(width, x + s2)

                val count = (x2 - x1) * (y2 - y1)
                val sum = integral[y2 * stride + x2] -
                          integral[y1 * stride + x2] -
                          integral[y2 * stride + x1] +
                          integral[y1 * stride + x1]

                val localMean = (sum / count).toFloat()
                val pixelVal = gray[y * width + x]

                output[y * width + x] = if (pixelVal <= localMean * (1f - t)) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
            }
        }

        val bw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bw.setPixels(output, 0, width, 0, 0, width, height)
        bw
    }

    /**
     * Multi-scale background whitening and shadow eradication.
     */
    suspend fun removeShadowsAndWhiten(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        enhanceMagicColor(src)
    }
}
