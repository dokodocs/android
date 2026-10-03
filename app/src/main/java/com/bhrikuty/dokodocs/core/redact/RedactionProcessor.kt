package com.bhrikuty.dokodocs.core.redact

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

enum class RedactionMode {
    BLACKOUT,
    BLUR,
    DEVANAGARI_MASK
}

data class RedactionRect(
    val normalizedRect: RectF, // 0.0 to 1.0 coordinates
    val label: String = "Redacted",
    val mode: RedactionMode = RedactionMode.BLACKOUT
)

object RedactionProcessor {

    fun applyRedactions(
        sourceBitmap: Bitmap,
        redactions: List<RedactionRect>
    ): Bitmap {
        if (redactions.isEmpty()) return sourceBitmap

        val result = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val blackPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val w = sourceBitmap.width.toFloat()
        val h = sourceBitmap.height.toFloat()

        for (redact in redactions) {
            val left = redact.normalizedRect.left * w
            val top = redact.normalizedRect.top * h
            val right = redact.normalizedRect.right * w
            val bottom = redact.normalizedRect.bottom * h

            val rect = RectF(left, top, right, bottom)
            canvas.drawRect(rect, blackPaint)

            // Draw small centered security badge
            val centerY = top + (bottom - top) / 2f + 10f
            val centerX = left + (right - left) / 2f
            canvas.drawText("REDACTED / गोप्य", centerX, centerY, textPaint)
        }

        return result
    }
}
