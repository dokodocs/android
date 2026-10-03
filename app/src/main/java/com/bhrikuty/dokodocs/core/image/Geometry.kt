package com.bhrikuty.dokodocs.core.image

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class Point2D(val x: Float, val y: Float) {
    fun toPointF(): PointF = PointF(x, y)

    operator fun plus(other: Point2D): Point2D = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D): Point2D = Point2D(x - other.x, y - other.y)
    operator fun times(scalar: Float): Point2D = Point2D(x * scalar, y * scalar)

    fun distanceTo(other: Point2D): Float = hypot(x - other.x, y - other.y)
}

enum class DetectionConfidence {
    HIGH,       // 4 visible corners verified
    ESTIMATED,  // 3 corners + 1 inferred, or vanishing-point projection
    MANUAL      // User adjusted
}

enum class PaperStandard(val displayName: String, val aspectRatio: Float) {
    A4("A4 (210 × 297 mm)", 1.4142f),
    A3("A3 (297 × 420 mm)", 1.4142f),
    A5("A5 (148 × 210 mm)", 1.4142f),
    LETTER("US Letter (8.5 × 11 in)", 1.2941f),
    LEGAL("US Legal (8.5 × 14 in)", 1.6471f),
    RECEIPT("Long Receipt", 2.2000f),
    ID_CARD("National ID / Card", 1.5858f),
    CUSTOM("Custom / Free Size", 1.0f);

    companion object {
        fun detectBestFit(width: Float, height: Float, tolerance: Float = 0.10f): PaperStandard {
            if (width <= 0f || height <= 0f) return CUSTOM
            val ratio = if (height >= width) height / width else width / height
            var bestMatch = CUSTOM
            var minDiff = Float.MAX_VALUE

            for (standard in entries) {
                if (standard == CUSTOM) continue
                val diff = abs(ratio - standard.aspectRatio)
                if (diff < minDiff && diff <= tolerance) {
                    minDiff = diff
                    bestMatch = standard
                }
            }
            return bestMatch
        }
    }
}

data class DocumentQuad(
    val topLeft: Point2D,
    val topRight: Point2D,
    val bottomRight: Point2D,
    val bottomLeft: Point2D,
    val confidence: DetectionConfidence = DetectionConfidence.HIGH,
    val isCurvedMesh: Boolean = false
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )

    fun scale(factorX: Float, factorY: Float): DocumentQuad = DocumentQuad(
        topLeft = Point2D(topLeft.x * factorX, topLeft.y * factorY),
        topRight = Point2D(topRight.x * factorX, topRight.y * factorY),
        bottomRight = Point2D(bottomRight.x * factorX, bottomRight.y * factorY),
        bottomLeft = Point2D(bottomLeft.x * factorX, bottomLeft.y * factorY),
        confidence = confidence,
        isCurvedMesh = isCurvedMesh
    )

    fun getEstimatedWidth(): Float {
        val topW = topLeft.distanceTo(topRight)
        val botW = bottomLeft.distanceTo(bottomRight)
        return max(topW, botW)
    }

    fun getEstimatedHeight(): Float {
        val leftH = topLeft.distanceTo(bottomLeft)
        val rightH = topRight.distanceTo(bottomRight)
        return max(leftH, rightH)
    }

    fun detectPaperStandard(): PaperStandard {
        return PaperStandard.detectBestFit(getEstimatedWidth(), getEstimatedHeight())
    }

    /**
     * Reconstructs a missing 4th corner when 3 corners are detected or known.
     */
    fun inferMissingCorner(missingCornerIndex: Int): DocumentQuad {
        return when (missingCornerIndex) {
            0 -> { // TopLeft missing -> p_tl = p_tr + (p_bl - p_br)
                val newTL = topRight + (bottomLeft - bottomRight)
                copy(topLeft = newTL, confidence = DetectionConfidence.ESTIMATED)
            }
            1 -> { // TopRight missing -> p_tr = p_tl + (p_br - p_bl)
                val newTR = topLeft + (bottomRight - bottomLeft)
                copy(topRight = newTR, confidence = DetectionConfidence.ESTIMATED)
            }
            2 -> { // BottomRight missing -> p_br = p_bl + (p_tr - p_tl)
                val newBR = bottomLeft + (topRight - topLeft)
                copy(bottomRight = newBR, confidence = DetectionConfidence.ESTIMATED)
            }
            3 -> { // BottomLeft missing -> p_bl = p_br + (p_tl - p_tr)
                val newBL = bottomRight + (topLeft - topRight)
                copy(bottomLeft = newBL, confidence = DetectionConfidence.ESTIMATED)
            }
            else -> this
        }
    }

    companion object {
        fun defaultFromSize(width: Float, height: Float, insetRatio: Float = 0.08f): DocumentQuad {
            val insetX = width * insetRatio
            val insetY = height * insetRatio
            return DocumentQuad(
                topLeft = Point2D(insetX, insetY),
                topRight = Point2D(width - insetX, insetY),
                bottomRight = Point2D(width - insetX, height - insetY),
                bottomLeft = Point2D(insetX, height - insetY),
                confidence = DetectionConfidence.HIGH
            )
        }

        /**
         * Intersects two line segments to find vanishing point corner.
         */
        fun intersectLines(
            l1p1: Point2D, l1p2: Point2D,
            l2p1: Point2D, l2p2: Point2D
        ): Point2D? {
            val a1 = l1p2.y - l1p1.y
            val b1 = l1p1.x - l1p2.x
            val c1 = a1 * l1p1.x + b1 * l1p1.y

            val a2 = l2p2.y - l2p1.y
            val b2 = l2p1.x - l2p2.x
            val c2 = a2 * l2p1.x + b2 * l2p1.y

            val determinant = a1 * b2 - a2 * b1
            if (abs(determinant) < 1e-5) return null // Parallel lines

            val x = (b2 * c1 - b1 * c2) / determinant
            val y = (a1 * c2 - a2 * c1) / determinant
            return Point2D(x, y)
        }
    }
}
