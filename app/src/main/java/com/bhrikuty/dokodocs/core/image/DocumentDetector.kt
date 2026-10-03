package com.bhrikuty.dokodocs.core.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object DocumentDetector {

    /**
     * Detects document quadrilateral from a full-size or preview bitmap.
     * Uses adaptive luminance thresholding + morphological close + connected component boundary + Ramer-Douglas-Peucker 4-corner extraction.
     */
    suspend fun detectDocument(bitmap: Bitmap): DocumentQuad = withContext(Dispatchers.Default) {
        val origW = bitmap.width
        val origH = bitmap.height

        // Downscale for ultra-fast, robust detection (target long edge ~320px)
        val targetSize = 320
        val scale = min(1f, targetSize.toFloat() / max(origW, origH))
        val sampleW = (origW * scale).toInt().coerceAtLeast(64)
        val sampleH = (origH * scale).toInt().coerceAtLeast(64)

        val sample = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        sample.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        // 1. Grayscale luminance
        val gray = IntArray(sampleW * sampleH)
        var sumLum = 0L
        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            gray[i] = lum
            sumLum += lum
        }

        // 2. Otsu thresholding to separate paper from background
        val avgLum = (sumLum / gray.size).toInt()
        val threshold = computeOtsuThreshold(gray, sampleW, sampleH, avgLum)

        // Binary mask (1 for paper, 0 for background)
        val binary = BooleanArray(sampleW * sampleH)
        for (i in gray.indices) {
            binary[i] = gray[i] > threshold
        }

        // 3. Morphological Close (Dilate 2x then Erode 2x) to connect text and bridge fold lines
        val closed = morphologicalClose(binary, sampleW, sampleH, radius = 2)

        // 4. Find largest connected paper region
        val component = findLargestConnectedComponent(closed, sampleW, sampleH)

        if (component.size < (sampleW * sampleH * 0.10f)) {
            // Document not distinct enough -> Return safe 6% inset quadrilateral
            return@withContext DocumentQuad.defaultFromSize(origW.toFloat(), origH.toFloat(), 0.06f)
        }

        // 5. Extract boundary perimeter points of the component
        val boundary = extractPerimeter(component, sampleW, sampleH)
        if (boundary.size < 20) {
            return@withContext DocumentQuad.defaultFromSize(origW.toFloat(), origH.toFloat(), 0.06f)
        }

        // 6. Compute Convex Hull
        val hull = computeConvexHull(boundary)

        // 7. Simplify polygon using Ramer-Douglas-Peucker algorithm to find 4 corners
        val perimeter = computePerimeter(hull)
        var epsilon = perimeter * 0.035f
        var simplified = ramerDouglasPeucker(hull, epsilon)

        // Fine tune epsilon if needed to reach 4 corners
        if (simplified.size > 4) {
            epsilon = perimeter * 0.055f
            val s2 = ramerDouglasPeucker(hull, epsilon)
            if (s2.size >= 4) simplified = s2
        }

        val corners = if (simplified.size == 4) {
            simplified
        } else {
            // Fit best 4 corners from convex hull by finding 4 extreme corners (TL, TR, BR, BL)
            findBestFourCorners(hull)
        }

        // 8. Order 4 corners: TL, TR, BR, BL
        val ordered = orderCorners(corners)

        // 9. Map back to original coordinate system
        val invScale = 1f / scale
        val mappedTL = Point2D(ordered[0].x * invScale, ordered[0].y * invScale)
        val mappedTR = Point2D(ordered[1].x * invScale, ordered[1].y * invScale)
        val mappedBR = Point2D(ordered[2].x * invScale, ordered[2].y * invScale)
        val mappedBL = Point2D(ordered[3].x * invScale, ordered[3].y * invScale)

        val quad = DocumentQuad(
            topLeft = clampPoint(mappedTL, origW.toFloat(), origH.toFloat()),
            topRight = clampPoint(mappedTR, origW.toFloat(), origH.toFloat()),
            bottomRight = clampPoint(mappedBR, origW.toFloat(), origH.toFloat()),
            bottomLeft = clampPoint(mappedBL, origW.toFloat(), origH.toFloat()),
            confidence = DetectionConfidence.HIGH
        )

        // Validate area & aspect ratio
        val area = computeQuadArea(quad)
        val frameArea = origW.toFloat() * origH.toFloat()
        if (area < frameArea * 0.12f || area > frameArea * 0.98f) {
            return@withContext DocumentQuad.defaultFromSize(origW.toFloat(), origH.toFloat(), 0.06f)
        }

        quad
    }

    private fun computeOtsuThreshold(gray: IntArray, w: Int, h: Int, defaultVal: Int): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v.coerceIn(0, 255)]++

        val total = w * h
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i]

        var sumB = 0.0
        var wB = 0
        var varMax = 0.0
        var bestThreshold = defaultVal

        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                bestThreshold = t
            }
        }
        return bestThreshold.coerceIn(70, 200)
    }

    private fun morphologicalClose(binary: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        // Dilate
        val dilated = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var isHit = false
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny !in 0 until h) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx in 0 until w && binary[ny * w + nx]) {
                            isHit = true
                            break
                        }
                    }
                    if (isHit) break
                }
                dilated[y * w + x] = isHit
            }
        }

        // Erode
        val eroded = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var allHit = true
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny !in 0 until h) {
                        allHit = false
                        break
                    }
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx !in 0 until w || !dilated[ny * w + nx]) {
                            allHit = false
                            break
                        }
                    }
                    if (!allHit) break
                }
                eroded[y * w + x] = allHit
            }
        }
        return eroded
    }

    private fun findLargestConnectedComponent(binary: BooleanArray, w: Int, h: Int): List<Point2D> {
        val visited = BooleanArray(w * h)
        var maxComponent = emptyList<Point2D>()

        val queue = ArrayDeque<Int>()

        for (y in 2 until h - 2 step 2) {
            for (x in 2 until w - 2 step 2) {
                val idx = y * w + x
                if (binary[idx] && !visited[idx]) {
                    val currentComponent = mutableListOf<Point2D>()
                    queue.add(idx)
                    visited[idx] = true

                    while (queue.isNotEmpty()) {
                        val curr = queue.poll() ?: break
                        val cx = curr % w
                        val cy = curr / w
                        currentComponent.add(Point2D(cx.toFloat(), cy.toFloat()))

                        val neighbors = intArrayOf(
                            if (cx > 0) curr - 1 else -1,
                            if (cx < w - 1) curr + 1 else -1,
                            if (cy > 0) curr - w else -1,
                            if (cy < h - 1) curr + w else -1
                        )

                        for (n in neighbors) {
                            if (n != -1 && binary[n] && !visited[n]) {
                                visited[n] = true
                                queue.add(n)
                            }
                        }
                    }

                    if (currentComponent.size > maxComponent.size) {
                        maxComponent = currentComponent
                    }
                }
            }
        }
        return maxComponent
    }

    private fun extractPerimeter(points: List<Point2D>, w: Int, h: Int): List<Point2D> {
        val grid = BooleanArray(w * h)
        for (p in points) {
            val ix = p.x.toInt().coerceIn(0, w - 1)
            val iy = p.y.toInt().coerceIn(0, h - 1)
            grid[iy * w + ix] = true
        }

        val perimeter = mutableListOf<Point2D>()
        for (p in points) {
            val ix = p.x.toInt()
            val iy = p.y.toInt()

            val isEdge = ix == 0 || ix == w - 1 || iy == 0 || iy == h - 1 ||
                    !grid[(iy - 1) * w + ix] || !grid[(iy + 1) * w + ix] ||
                    !grid[iy * w + (ix - 1)] || !grid[iy * w + (ix + 1)]

            if (isEdge) {
                perimeter.add(p)
            }
        }
        return perimeter
    }

    private fun computeConvexHull(points: List<Point2D>): List<Point2D> {
        if (points.size <= 3) return points

        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))

        val lower = mutableListOf<Point2D>()
        for (p in sorted) {
            while (lower.size >= 2 && crossProduct(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }

        val upper = mutableListOf<Point2D>()
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2 && crossProduct(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }

        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    private fun crossProduct(a: Point2D, b: Point2D, c: Point2D): Float {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    }

    private fun computePerimeter(points: List<Point2D>): Float {
        var p = 0f
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            p += hypot(p2.x - p1.x, p2.y - p1.y)
        }
        return p
    }

    private fun ramerDouglasPeucker(points: List<Point2D>, epsilon: Float): List<Point2D> {
        if (points.size < 3) return points

        var maxDist = 0f
        var index = 0

        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val left = ramerDouglasPeucker(points.subList(0, index + 1), epsilon)
            val right = ramerDouglasPeucker(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(pt: Point2D, lineStart: Point2D, lineEnd: Point2D): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val mag = hypot(dx, dy)
        if (mag < 1e-6f) return hypot(pt.x - lineStart.x, pt.y - lineStart.y)
        return abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x) / mag
    }

    private fun findBestFourCorners(hull: List<Point2D>): List<Point2D> {
        if (hull.size < 4) return hull

        // Find 4 extreme points based on (x + y), (x - y)
        var tl = hull[0]
        var tr = hull[0]
        var br = hull[0]
        var bl = hull[0]

        var minSum = Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE

        for (p in hull) {
            val sum = p.x + p.y
            val diff = p.x - p.y

            if (sum < minSum) {
                minSum = sum
                tl = p
            }
            if (sum > maxSum) {
                maxSum = sum
                br = p
            }
            if (diff > maxDiff) {
                maxDiff = diff
                tr = p
            }
            if (diff < minDiff) {
                minDiff = diff
                bl = p
            }
        }

        return listOf(tl, tr, br, bl)
    }

    private fun orderCorners(corners: List<Point2D>): List<Point2D> {
        if (corners.size != 4) return corners

        // Compute centroid
        val cx = (corners[0].x + corners[1].x + corners[2].x + corners[3].x) / 4f
        val cy = (corners[0].y + corners[1].y + corners[2].y + corners[3].y) / 4f

        // Sort in polar angle clockwise order
        val sortedClockwise = corners.sortedBy { p ->
            atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())
        }

        // Find the top-left anchor point (smallest sum of x + y)
        var bestIdx = 0
        var minSum = Float.MAX_VALUE
        for (i in sortedClockwise.indices) {
            val sum = sortedClockwise[i].x + sortedClockwise[i].y
            if (sum < minSum) {
                minSum = sum
                bestIdx = i
            }
        }

        val tl = sortedClockwise[bestIdx]
        val tr = sortedClockwise[(bestIdx + 1) % 4]
        val br = sortedClockwise[(bestIdx + 2) % 4]
        val bl = sortedClockwise[(bestIdx + 3) % 4]

        return listOf(tl, tr, br, bl)
    }

    private fun clampPoint(p: Point2D, maxW: Float, maxH: Float): Point2D {
        return Point2D(
            p.x.coerceIn(0f, maxW),
            p.y.coerceIn(0f, maxH)
        )
    }

    private fun computeQuadArea(q: DocumentQuad): Float {
        return 0.5f * abs(
            (q.topLeft.x * q.topRight.y + q.topRight.x * q.bottomRight.y + q.bottomRight.x * q.bottomLeft.y + q.bottomLeft.x * q.topLeft.y) -
                    (q.topLeft.y * q.topRight.x + q.topRight.y * q.bottomRight.x + q.bottomRight.y * q.bottomLeft.x + q.bottomLeft.y * q.topLeft.x)
        )
    }
}
