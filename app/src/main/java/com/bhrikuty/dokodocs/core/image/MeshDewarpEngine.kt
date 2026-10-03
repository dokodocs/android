package com.bhrikuty.dokodocs.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max

object MeshDewarpEngine {

    /**
     * Dewarps a curved or bent document using an 8x8 or 16x16 Bilinear Spline Mesh grid.
     */
    suspend fun dewarpCurvedDocument(
        srcBitmap: Bitmap,
        quad: DocumentQuad,
        meshCols: Int = 8,
        meshRows: Int = 8
    ): Bitmap = withContext(Dispatchers.Default) {
        val widthTop = hypot((quad.topRight.x - quad.topLeft.x).toDouble(), (quad.topRight.y - quad.topLeft.y).toDouble())
        val widthBottom = hypot((quad.bottomRight.x - quad.bottomLeft.x).toDouble(), (quad.bottomRight.y - quad.bottomLeft.y).toDouble())
        val targetWidth = max(widthTop, widthBottom).toInt().coerceIn(200, 4000)

        val heightLeft = hypot((quad.bottomLeft.x - quad.topLeft.x).toDouble(), (quad.bottomLeft.y - quad.topLeft.y).toDouble())
        val heightRight = hypot((quad.bottomRight.x - quad.topRight.x).toDouble(), (quad.bottomRight.y - quad.topRight.y).toDouble())
        val targetHeight = max(heightLeft, heightRight).toInt().coerceIn(200, 4000)

        val totalPoints = (meshCols + 1) * (meshRows + 1)
        val verts = FloatArray(totalPoints * 2)

        var index = 0
        for (row in 0..meshRows) {
            val v = row.toFloat() / meshRows.toFloat()
            for (col in 0..meshCols) {
                val u = col.toFloat() / meshCols.toFloat()

                // Coons Patch Bilinear interpolation from 4 boundary corners
                val topX = (1 - u) * quad.topLeft.x + u * quad.topRight.x
                val topY = (1 - u) * quad.topLeft.y + u * quad.topRight.y

                val botX = (1 - u) * quad.bottomLeft.x + u * quad.bottomRight.x
                val botY = (1 - u) * quad.bottomLeft.y + u * quad.bottomRight.y

                val leftX = (1 - v) * quad.topLeft.x + v * quad.bottomLeft.x
                val leftY = (1 - v) * quad.topLeft.y + v * quad.bottomLeft.y

                val rightX = (1 - v) * quad.topRight.x + v * quad.bottomRight.x
                val rightY = (1 - v) * quad.topRight.y + v * quad.bottomRight.y

                val cornerX = (1 - u) * (1 - v) * quad.topLeft.x +
                              u * (1 - v) * quad.topRight.x +
                              (1 - u) * v * quad.bottomLeft.x +
                              u * v * quad.bottomRight.x

                val cornerY = (1 - u) * (1 - v) * quad.topLeft.y +
                              u * (1 - v) * quad.topRight.y +
                              (1 - u) * v * quad.bottomLeft.y +
                              u * v * quad.bottomRight.y

                val x = (1 - v) * topX + v * botX + (1 - u) * leftX + u * rightX - cornerX
                val y = (1 - v) * topY + v * botY + (1 - u) * leftY + u * rightY - cornerY

                verts[index * 2] = x
                verts[index * 2 + 1] = y
                index++
            }
        }

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Native Hardware DrawBitmapMesh
        canvas.drawBitmapMesh(srcBitmap, meshCols, meshRows, verts, 0, null, 0, paint)

        outputBitmap
    }
}
