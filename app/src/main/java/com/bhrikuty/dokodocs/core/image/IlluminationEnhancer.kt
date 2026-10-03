package com.bhrikuty.dokodocs.core.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object IlluminationEnhancer {

    /**
     * Multi-scale background whitening and crease shadow attenuation.
     * Retains crisp black/colored text while normalizing uneven shadows.
     */
    suspend fun removeShadowsAndWhiten(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val output = IntArray(width * height)

        // Fast block-based background luminance estimation (32x32 blocks)
        val blockSize = 32
        val blocksX = (width + blockSize - 1) / blockSize
        val blocksY = (height + blockSize - 1) / blockSize
        val backgroundGrid = Array(blocksY) { IntArray(blocksX) }

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var maxLum = 0
                for (y in (by * blockSize) until min((by + 1) * blockSize, height)) {
                    for (x in (bx * blockSize) until min((bx + 1) * blockSize, width)) {
                        val p = pixels[y * width + x]
                        val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                        if (lum > maxLum) maxLum = lum
                    }
                }
                backgroundGrid[by][bx] = max(maxLum, 140)
            }
        }

        for (y in 0 until height) {
            val by = (y / blockSize).coerceIn(0, blocksY - 1)
            for (x in 0 until width) {
                val bx = (x / blockSize).coerceIn(0, blocksX - 1)
                val bgLum = backgroundGrid[by][bx].toFloat()

                val p = pixels[y * width + x]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                val gain = (255f / bgLum).coerceIn(1.0f, 2.2f)

                val newR = min(255, (r * gain).toInt())
                val newG = min(255, (g * gain).toInt())
                val newB = min(255, (b * gain).toInt())

                output[y * width + x] = Color.rgb(newR, newG, newB)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, width, 0, 0, width, height)
        result
    }
}
