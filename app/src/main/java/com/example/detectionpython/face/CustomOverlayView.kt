package com.example.detectionpython.face

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class CustomOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw a rectangular bounding box
        val left = width * 0.1f
        val top = height * 0.2f
        val right = width * 0.9f
        val bottom = height * 0.8f
        canvas.drawRect(left, top, right, bottom, paint)
    }
}
