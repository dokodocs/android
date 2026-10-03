package com.bhrikuty.dokodocs.core.quality

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import com.bhrikuty.dokodocs.core.image.DocumentQuad
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class IssueSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class QualityIssueType {
    MISSING_CORNER,
    REGIONAL_BLUR,
    GLARE_REFLECTION,
    CREASE_SHADOW,
    LOW_RESOLUTION,
    SEVERE_SKEW
}

data class QualityIssue(
    val type: QualityIssueType,
    val severity: IssueSeverity,
    val messageEn: String,
    val messageNe: String,
    val regionDescription: String
)

data class QualityReport(
    val score: Int, // 0 to 100
    val issues: List<QualityIssue>,
    val isRetakeRecommended: Boolean,
    val grade: String // "A" (90-100), "B" (75-89), "C" (50-74), "D" (<50)
) {
    val isGoodQuality: Boolean get() = score >= 75
}

object QualityAnalyzer {

    fun analyzeQuality(
        bitmap: Bitmap,
        quad: DocumentQuad? = null,
        expectedAspect: Float? = null
    ): QualityReport {
        val issues = mutableListOf<QualityIssue>()
        var score = 100

        val width = bitmap.width
        val height = bitmap.height

        // 1. Check Missing / Cut-off Corners
        if (quad != null) {
            val margin = 10f // px
            val points = listOf(
                "Top-Left" to "माथिल्लो-बायाँ" to quad.topLeft,
                "Top-Right" to "माथिल्लो-दायाँ" to quad.topRight,
                "Bottom-Right" to "तल्लो-दायाँ" to quad.bottomRight,
                "Bottom-Left" to "तल्लो-बायाँ" to quad.bottomLeft
            )

            for (item in points) {
                val names = item.first
                val pt = item.second
                val nameEn = names.first
                val nameNe = names.second

                // Check if corner touches or falls outside frame boundary
                if (pt.x <= margin || pt.x >= width - margin || pt.y <= margin || pt.y >= height - margin) {
                    issues.add(
                        QualityIssue(
                            type = QualityIssueType.MISSING_CORNER,
                            severity = IssueSeverity.WARNING,
                            messageEn = "Your document is missing or touching the edge at the $nameEn corner.",
                            messageNe = "कागजातको $nameNe कुना काटिएको वा फ्रेम बाहिर परेको छ।",
                            regionDescription = nameEn
                        )
                    )
                    score -= 12
                }
            }
        }

        // Downscale for fast grid analysis
        val targetSize = 240
        val scale = min(1f, targetSize.toFloat() / max(width, height))
        val sampleW = (width * scale).toInt().coerceAtLeast(32)
        val sampleH = (height * scale).toInt().coerceAtLeast(32)

        val sampleBitmap = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        sampleBitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        // 2. Regional Blur Analysis (4x4 Grid Laplacian Variance)
        val gridCols = 4
        val gridRows = 4
        val cellW = sampleW / gridCols
        val cellH = sampleH / gridRows

        val blurVariances = mutableListOf<Float>()
        var minBlurVariance = Float.MAX_VALUE
        var minBlurCell = Pair(0, 0)

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                var sumLuma = 0.0
                var sumLumaSq = 0.0
                var edgeSum = 0.0
                var pixelCount = 0

                for (y in (r * cellH) until ((r + 1) * cellH)) {
                    for (x in (c * cellW) until ((c + 1) * cellW)) {
                        val color = pixels[y * sampleW + x]
                        val luma = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
                        sumLuma += luma
                        sumLumaSq += luma * luma
                        pixelCount++

                        // Simple horizontal gradient approximation
                        if (x < (c + 1) * cellW - 1) {
                            val rightColor = pixels[y * sampleW + x + 1]
                            val rightLuma = 0.299 * Color.red(rightColor) + 0.587 * Color.green(rightColor) + 0.114 * Color.blue(rightColor)
                            edgeSum += abs(luma - rightLuma)
                        }
                    }
                }

                if (pixelCount > 0) {
                    val mean = sumLuma / pixelCount
                    val variance = (sumLumaSq / pixelCount) - (mean * mean)
                    val edgeDensity = (edgeSum / pixelCount).toFloat()

                    // Low edge density + low variance on ink region = potential blur
                    val cellBlurScore = edgeDensity * 10f + variance.toFloat().coerceAtMost(500f) / 10f
                    blurVariances.add(cellBlurScore)

                    if (cellBlurScore < minBlurVariance) {
                        minBlurVariance = cellBlurScore
                        minBlurCell = Pair(r, c)
                    }
                }
            }
        }

        // Average variance across all cells
        val avgVariance = if (blurVariances.isNotEmpty()) blurVariances.average().toFloat() else 0f
        if (avgVariance < 18f && minBlurVariance < 8f) {
            val cellLoc = getCellLocationName(minBlurCell.first, minBlurCell.second, gridRows, gridCols)
            issues.add(
                QualityIssue(
                    type = QualityIssueType.REGIONAL_BLUR,
                    severity = IssueSeverity.CRITICAL,
                    messageEn = "Blurry text detected in ${cellLoc.first} area. Hold camera steady.",
                    messageNe = "${cellLoc.second} भागमा धमिलो देखिएको छ। क्यामेरा स्थिर राख्नुहोस्।",
                    regionDescription = cellLoc.first
                )
            )
            score -= 20
        }

        // 3. Glare / Reflection Detection
        var saturatedPixels = 0
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            if (r > 248 && g > 248 && b > 248) {
                saturatedPixels++
            }
        }
        val glareRatio = saturatedPixels.toFloat() / pixels.size
        if (glareRatio > 0.08f) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.GLARE_REFLECTION,
                    severity = IssueSeverity.WARNING,
                    messageEn = "Strong lighting glare or reflection detected. Adjust camera angle.",
                    messageNe = "कागजातमा बत्तीको कडा चमक/परावर्तन देखिएको छ।",
                    regionDescription = "Center / Highlights"
                )
            )
            score -= 15
        }

        // 4. Resolution check
        if (width < 800 || height < 800) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.LOW_RESOLUTION,
                    severity = IssueSeverity.WARNING,
                    messageEn = "Image resolution is low. Small text may be hard to read.",
                    messageNe = "फोटोको रिजोल्युसन कम छ। साना अक्षरहरू पढ्न गाह्रो हुन सक्छ।",
                    regionDescription = "Full page"
                )
            )
            score -= 10
        }

        val finalScore = score.coerceIn(0, 100)
        val grade = when {
            finalScore >= 90 -> "A"
            finalScore >= 75 -> "B"
            finalScore >= 55 -> "C"
            else -> "D"
        }

        return QualityReport(
            score = finalScore,
            issues = issues,
            isRetakeRecommended = finalScore < 60,
            grade = grade
        )
    }

    private fun getCellLocationName(row: Int, col: Int, totalRows: Int, totalCols: Int): Pair<String, String> {
        val vertical = when {
            row < totalRows / 3 -> "Top" to "माथिल्लो"
            row >= 2 * totalRows / 3 -> "Bottom" to "तल्लो"
            else -> "Middle" to "मध्य"
        }
        val horizontal = when {
            col < totalCols / 3 -> "Left" to "बायाँ"
            col >= 2 * totalCols / 3 -> "Right" to "दायाँ"
            else -> "Center" to "केन्द्र"
        }
        return "${vertical.first}-${horizontal.first}" to "${vertical.second}-${horizontal.second}"
    }
}
