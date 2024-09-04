package com.example.detectionpython.face

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.face.Face

class FaceContourGraphic(
    overlay: GraphicOverlay,
    private val face: Face,
    private val imageRect: Rect,
    private val onSuccessCallback: (FaceStatus) -> Unit,
    private val source: String
) : GraphicOverlay.Graphic(overlay) {

    private val facePositionPaint: Paint
    private val boxPaint: Paint
    private val textPaint: Paint
    private val greenBoxPaint: Paint
    private val redBoxPaint: Paint
    private var currentTooltipText: String = ""

    // These variables will replace the companion object constants
    private val boxStrokeWidth: Float
    private val centerTolerance: Float
    private val minScreenPercentage: Float
    private val textSize: Float
    private val textMargin: Float
    private val lineSpacing: Float

    init {
        // Initialize variables based on the source
        when (source) {
            "registration" -> {
                boxStrokeWidth = 5.0f
                centerTolerance = 0.13f
                minScreenPercentage = 0.20f
                textSize = 48f
                textMargin = 100f
                lineSpacing = 10f
            }
            "mainactivity" -> {
                boxStrokeWidth = 5.0f
                centerTolerance = 0.50f
                minScreenPercentage = 0.005f
                textSize = 48f
                textMargin = 100f
                lineSpacing = 10f
            }
            else -> {
                // Default values
                boxStrokeWidth = 5.0f
                centerTolerance = 0.17f
                minScreenPercentage = 0.25f
                textSize = 48f
                textMargin = 100f
                lineSpacing = 10f
            }
        }

        val selectedColor = Color.WHITE

        facePositionPaint = Paint().apply { color = selectedColor }
        boxPaint = Paint().apply {
            color = selectedColor
            style = Paint.Style.STROKE
            strokeWidth = boxStrokeWidth
        }

        greenBoxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = boxStrokeWidth
        }

        redBoxPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = boxStrokeWidth
        }

        textPaint = Paint().apply {
            color = Color.WHITE
            textSize = this@FaceContourGraphic.textSize
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
        }
    }

    override fun draw(canvas: Canvas?) {
        val rect = calculateRect(
            imageRect.height().toFloat(),
            imageRect.width().toFloat(),
            face.boundingBox
        )
        val faceDimensions = getFaceDimensions()
        val tooltipText: String

        when {
            checkIsTooFar(faceDimensions) -> {
                onSuccessCallback(FaceStatus.TOO_FAR)
                canvas?.drawRect(rect, redBoxPaint)
                tooltipText = "Your face is too far\nPlease move closer to the camera for better accuracy."
                textPaint.color = Color.RED
            }
            checkIsNotCentered(faceDimensions) -> {
                onSuccessCallback(FaceStatus.NOT_CENTERED)
                canvas?.drawRect(rect, redBoxPaint)
                tooltipText = "Your Face is not centered"
                textPaint.color = Color.RED
            }
            else -> {
                onSuccessCallback(FaceStatus.VALID)
                canvas?.drawRect(rect, greenBoxPaint)
                tooltipText = "Your face is well-positioned"

                textPaint.color = Color.GREEN
            }
        }

        // Store the current tooltip text
        currentTooltipText = tooltipText

        // Draw tooltip text above the rectangle
        drawTooltipText(canvas, rect, tooltipText)
    }

    fun getCurrentTooltipText(): String = currentTooltipText

    private fun drawTooltipText(canvas: Canvas?, rect: RectF, text: String) {
        val lines = text.split("\n")
        val x = rect.centerX()

        lines.forEachIndexed { index, line ->
            val y = rect.top - textMargin - (lines.size - 1 - index) * (textPaint.textSize + lineSpacing)
            canvas?.drawText(line, x, y, textPaint)
        }
    }

    private fun checkIsNotCentered(faceDimensions: FaceDimensions): Boolean {
        val width = imageRect.width()
        val height = imageRect.height()
        val centerX = imageRect.centerX()
        val centerY = imageRect.centerY()
        val toleranceX = width * centerTolerance
        val toleranceY = height * centerTolerance

        return faceDimensions.x < centerX - toleranceX ||
                faceDimensions.x > centerX + toleranceX ||
                faceDimensions.y < centerY - toleranceY ||
                faceDimensions.y > centerY + toleranceY
    }

    private fun checkIsTooFar(faceDimensions: FaceDimensions): Boolean {
        val width = imageRect.width()
        val height = imageRect.height()

        return (faceDimensions.bottom - faceDimensions.top <= height * minScreenPercentage ||
                faceDimensions.right - faceDimensions.left <= width * minScreenPercentage)
    }

    private fun getFaceDimensions(): FaceDimensions {
        val screenWidth = imageRect.width().toFloat()
        val screenHeight = imageRect.height().toFloat()

        // Calculate the initial bounding box coordinates
        val x = face.boundingBox.centerX().toFloat()
        val y = face.boundingBox.centerY().toFloat()

        // Calculate the left, top, right, and bottom coordinates
        var left = x - face.boundingBox.width() / 2.0f
        var top = y - face.boundingBox.height() / 2.0f
        var right = x + face.boundingBox.width() / 2.0f
        var bottom = y + face.boundingBox.height() / 2.0f

        // Clamp the coordinates to ensure the box stays within the screen bounds
        left = left.coerceAtLeast(0f)
        top = top.coerceAtLeast(0f)
        right = right.coerceAtMost(screenWidth)
        bottom = bottom.coerceAtMost(screenHeight)

        return FaceDimensions(
            x = x,
            y = y,
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    }

}

data class FaceDimensions(
    val x: Float,
    val y: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
