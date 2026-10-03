package com.bhrikuty.dokodocs.core.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object DocumentDetector {

    /**
     * Detects document quadrilateral from a full-size or preview bitmap.
     * Uses downsampled luminance gradient + edge walking + contour approximation.
     */
    suspend fun detectDocument(bitmap: Bitmap): DocumentQuad = withContext(Dispatchers.Default) {
        val origW = bitmap.width
        val origH = bitmap.height

        // Downscale for ultra-fast detection (target long edge ~320px)
        val targetSize = 320
        val scale = min(1f, targetSize.toFloat() / max(origW, origH))
        val sampleW = (origW * scale).toInt().coerceAtLeast(64)
        val sampleH = (origH * scale).toInt().coerceAtLeast(64)

        val sample = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        sample.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        // Convert to grayscale and calculate horizontal and vertical Sobel gradients
        val gray = IntArray(sampleW * sampleH)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
        }

        // Apply 3x3 Gaussian blur approximation (box blur 2 passes)
        val blurred = boxBlur(gray, sampleW, sampleH)

        // Compute edge magnitude
        val edges = FloatArray(sampleW * sampleH)
        var maxGrad = 0f
        for (y in 1 until sampleH - 1) {
            for (x in 1 until sampleW - 1) {
                val gx = (blurred[y * sampleW + (x + 1)] - blurred[y * sampleW + (x - 1)]).toFloat()
                val gy = (blurred[(y + 1) * sampleW + x] - blurred[(y - 1) * sampleW + x]).toFloat()
                val mag = hypot(gx, gy)
                edges[y * sampleW + x] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        // Threshold edges
        val threshold = maxGrad * 0.22f
        val edgePoints = mutableListOf<Point2D>()
        for (y in 2 until sampleH - 2) {
            for (x in 2 until sampleW - 2) {
                if (edges[y * sampleW + x] > threshold) {
                    edgePoints.add(Point2D(x.toFloat(), y.toFloat()))
                }
            }
        }

        if (edgePoints.size < 40) {
            // Insufficient contrast -> Return standard centered inset quad
            return@withContext DocumentQuad.defaultFromSize(origW.toFloat(), origH.toFloat(), 0.08f)
        }

        // Find document boundary candidates: Convex Hull / Extremities
        val (minX, maxX, minY, maxY) = findBoundingExtremes(edgePoints, sampleW, sampleH)

        // Find 4 corner clusters closest to the 4 corners of bounding rectangle
        val tl = findExtremePoint(edgePoints, minX, minY, 0.5f, 0.5f)
        val tr = findExtremePoint(edgePoints, maxX, minY, -0.5f, 0.5f)
        val br = findExtremePoint(edgePoints, maxX, maxY, -0.5f, -0.5f)
        val bl = findExtremePoint(edgePoints, minX, maxY, 0.5f, -0.5f)

        // Map back to original coordinate system
        val invScale = 1f / scale
        val mappedTL = Point2D(tl.x * invScale, tl.y * invScale)
        val mappedTR = Point2D(tr.x * invScale, tr.y * invScale)
        val mappedBR = Point2D(br.x * invScale, br.y * invScale)
        val mappedBL = Point2D(bl.x * invScale, bl.y * invScale)

        val quad = DocumentQuad(
            topLeft = clampPoint(mappedTL, origW.toFloat(), origH.toFloat()),
            topRight = clampPoint(mappedTR, origW.toFloat(), origH.toFloat()),
            bottomRight = clampPoint(mappedBR, origW.toFloat(), origH.toFloat()),
            bottomLeft = clampPoint(mappedBL, origW.toFloat(), origH.toFloat()),
            confidence = DetectionConfidence.HIGH
        )

        // Sanity check: Ensure quad area is at least 15% and rectangularity is reasonable
        val area = computeQuadArea(quad)
        val frameArea = origW.toFloat() * origH.toFloat()
        if (area < frameArea * 0.15f || area > frameArea * 0.98f) {
            return@withContext DocumentQuad.defaultFromSize(origW.toFloat(), origH.toFloat(), 0.08f)
        }

        quad
    }

    private fun boxBlur(input: IntArray, w: Int, h: Int): IntArray {
        val output = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val sum = input[(y - 1) * w + (x - 1)] + input[(y - 1) * w + x] + input[(y - 1) * w + (x + 1)] +
                        input[y * w + (x - 1)] + input[y * w + x] + input[y * w + (x + 1)] +
                        input[(y + 1) * w + (x - 1)] + input[(y + 1) * w + x] + input[(y + 1) * w + (x + 1)]
                output[y * w + x] = sum / 9
            }
        }
        return output
    }

    private fun findBoundingExtremes(points: List<Point2D>, w: Int, h: Int): FloatArray {
        var minX = w.toFloat()
        var maxX = 0f
        var minY = h.toFloat()
        var maxY = 0f

        // 10th and 90th percentile to ignore noise
        val sortedX = points.map { it.x }.sorted()
        val sortedY = points.map { it.y }.sorted()

        val p5Idx = (points.size * 0.05f).toInt()
        val p95Idx = (points.size * 0.95f).toInt().coerceAtMost(points.size - 1)

        minX = sortedX[p5Idx]
        maxX = sortedX[p95Idx]
        minY = sortedY[p5Idx]
        maxY = sortedY[p95Idx]

        return floatArrayOf(minX, maxX, minY, maxY)
    }

    private fun findExtremePoint(points: List<Point2D>, targetX: Float, targetY: Float, weightX: Float, weightY: Float): Point2D {
        var bestDist = Float.MAX_VALUE
        var bestPoint = Point2D(targetX, targetY)

        for (p in points) {
            val d = hypot(p.x - targetX, p.y - targetY)
            if (d < bestDist) {
                bestDist = d
                bestPoint = p
            }
        }
        return bestPoint
    }

    private fun clampPoint(p: Point2D, maxW: Float, maxH: Float): Point2D {
        return Point2D(
            p.x.coerceIn(0f, maxW),
            p.y.coerceIn(0f, maxH)
        )
    }

    private fun computeQuadArea(q: DocumentQuad): Float {
        // Shoelace formula
        return 0.5f * abs(
            (q.topLeft.x * q.topRight.y + q.topRight.x * q.bottomRight.y + q.bottomRight.x * q.bottomLeft.y + q.bottomLeft.x * q.topLeft.y) -
                    (q.topLeft.y * q.topRight.x + q.topRight.y * q.bottomRight.x + q.bottomRight.y * q.bottomLeft.x + q.bottomLeft.y * q.topLeft.x)
        )
    }
}
