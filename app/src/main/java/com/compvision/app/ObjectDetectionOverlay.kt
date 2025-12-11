package com.compvision.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class ObjectDetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val detections = mutableListOf<YOLODetector.Detection>()
    private var imageWidth = 1
    private var imageHeight = 1
    
    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.detection_box)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.detection_text)
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val textBgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 180
    }
    
    fun setDetections(newDetections: List<YOLODetector.Detection>, imgWidth: Int, imgHeight: Int) {
        detections.clear()
        detections.addAll(newDetections)
        imageWidth = imgWidth
        imageHeight = imgHeight
        postInvalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (detections.isEmpty() || imageWidth == 0 || imageHeight == 0) {
            return
        }
        
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        
        for (detection in detections) {
            val box = detection.box
            val scaledBox = RectF(
                box.left * scaleX,
                box.top * scaleY,
                box.right * scaleX,
                box.bottom * scaleY
            )
            
            // Draw bounding box
            canvas.drawRect(scaledBox, boxPaint)
            
            // Draw label background
            val label = "${detection.className} ${(detection.confidence * 100).toInt()}%"
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            
            val textX = scaledBox.left
            val textY = scaledBox.top - textBounds.height() - 10
            
            val bgRect = RectF(
                textX - 10,
                textY - 10,
                textX + textBounds.width() + 10,
                textY + textBounds.height() + 10
            )
            
            canvas.drawRect(bgRect, textBgPaint)
            canvas.drawText(label, textX, textY, textPaint)
        }
    }
}





