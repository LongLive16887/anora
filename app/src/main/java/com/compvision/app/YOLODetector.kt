package com.compvision.app

import android.content.Context
import android.graphics.*
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class YOLODetector(
    private val context: Context,
    private val overlay: ObjectDetectionOverlay,
    private val statusCallback: ((String) -> Unit)? = null,
    private val objectCountCallback: ((Map<String, Int>) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    
    private var interpreter: Interpreter? = null
    private lateinit var inputBuffer: ByteBuffer
    private var outputBuffer: Array<Array<FloatArray>>? = null // YOLOv8 format: [1, 84, 8400]
    private val modelInputSize = 640
    private val modelOutputSize = 8400 // YOLO 8n output size (number of anchor points)
    private val numClasses = 80 // COCO dataset classes
    private val confidenceThreshold = 0.5f
    private val nmsThreshold = 0.4f
    private var outputShape: IntArray? = null
    
    // COCO class names
    private val classNames = arrayOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
        "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
        "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
        "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
        "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
        "toothbrush"
    )
    
    init {
        try {
            Log.d("YOLODetector", "init: Starting model loading")
            statusCallback?.invoke("Чтение файла модели...")
            
            // Try to load the model from assets
            // Note: You need to place yolov8n.tflite in app/src/main/assets/
            val modelBuffer = loadModelFile("yolov8n.tflite")
            Log.d("YOLODetector", "init: Model file loaded, size: ${modelBuffer.capacity()} bytes")
            
            statusCallback?.invoke("Инициализация интерпретатора...")
            interpreter = Interpreter(modelBuffer)
            Log.d("YOLODetector", "init: Interpreter created")
            
            // Get output shape from model
            statusCallback?.invoke("Анализ структуры модели...")
            val outputTensor = interpreter?.getOutputTensor(0)
            outputShape = outputTensor?.shape()
            Log.d("YOLODetector", "Model output shape: ${outputShape?.contentToString()}")
            
            // Allocate buffers
            statusCallback?.invoke("Выделение памяти...")
            inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
            inputBuffer.order(ByteOrder.nativeOrder())
            Log.d("YOLODetector", "init: Input buffer allocated")
            
            // YOLOv8 output format: [1, 84, 8400] where 84 = 4 (bbox) + 80 (classes)
            // We need to match the exact shape from the model
            val shape = outputShape ?: intArrayOf(1, 84, 8400)
            val batchSize = shape[0]
            val features = shape[1] // Should be 84
            val detections = shape[2] // Should be 8400
            
            Log.d("YOLODetector", "init: Allocating output buffer with shape [$batchSize, $features, $detections]")
            outputBuffer = Array(batchSize) { Array(features) { FloatArray(detections) } }
            Log.d("YOLODetector", "init: Output buffer allocated")
            
            Log.d("YOLODetector", "Model loaded successfully")
            statusCallback?.invoke("Модель загружена")
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error loading model: ${e.message}", e)
            statusCallback?.invoke("Ошибка загрузки модели: ${e.message}")
            // Initialize empty buffers to prevent crashes
            inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3)
            inputBuffer.order(ByteOrder.nativeOrder())
            outputBuffer = Array(1) { Array(84) { FloatArray(modelOutputSize) } }
        }
    }
    
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        Log.d("YOLODetector", "loadModelFile: Loading $modelPath from assets")
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd(modelPath)
        Log.d("YOLODetector", "loadModelFile: File descriptor opened, size: ${fileDescriptor.declaredLength} bytes")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        Log.d("YOLODetector", "loadModelFile: Model mapped to memory")
        return buffer
    }
    
    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()
    private var lastProcessedTime = 0L
    private val frameSkipInterval = 3 // Process every 3rd frame
    private val minProcessingInterval = 200L // Minimum 200ms between processing (max 5 FPS)
    
    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (interpreter == null || !::inputBuffer.isInitialized || outputBuffer == null) {
                imageProxy.close()
                return
            }
            
            frameCount++
            val currentTime = System.currentTimeMillis()
            
            // Skip frames to improve performance
            if (frameCount % frameSkipInterval != 0) {
                imageProxy.close()
                return
            }
            
            // Rate limit processing
            if (currentTime - lastProcessedTime < minProcessingInterval) {
                imageProxy.close()
                return
            }
            
            if (currentTime - lastLogTime > 5000) { // Log every 5 seconds
                Log.d("YOLODetector", "analyze: Processing frame $frameCount")
                lastLogTime = currentTime
            }
            
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                imageProxy.close()
                return
            }
            
            lastProcessedTime = currentTime
            val detections = detectObjects(bitmap)
            
            if (frameCount == frameSkipInterval) {
                Log.d("YOLODetector", "analyze: First frame processed, detections: ${detections.size}")
            }
            
            // Count objects by class
            val objectCounts = detections.groupingBy { it.className }.eachCount()
            
            overlay.post {
                overlay.setDetections(detections, bitmap.width, bitmap.height)
            }
            
            // Update object counts on UI thread
            objectCountCallback?.invoke(objectCounts)
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error in analyze: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        try {
            val planes = imageProxy.planes
            
            // Check if we have YUV format with at least 2 planes
            if (planes.size < 2) {
                Log.e("YOLODetector", "Unexpected image format: ${planes.size} planes")
                return null
            }
            
            val width = imageProxy.width
            val height = imageProxy.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // Create bitmap directly from YUV planes - more efficient than JPEG conversion
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = if (planes.size > 2) planes[2] else null
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane?.buffer
            
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane?.rowStride ?: uRowStride
            val vPixelStride = vPlane?.pixelStride ?: uPixelStride
            
            // Read buffers into arrays for faster access
            val yArray = ByteArray(yBuffer.remaining())
            yBuffer.get(yArray)
            val uArray = ByteArray(uBuffer.remaining())
            uBuffer.get(uArray)
            val vArray = if (vBuffer != null) {
                val arr = ByteArray(vBuffer.remaining())
                vBuffer.get(arr)
                arr
            } else null
            
            val pixels = IntArray(width * height)
            var pixelIndex = 0
            
            for (y in 0 until height) {
                val yRowOffset = y * yRowStride
                val uvRowOffset = (y / 2) * uRowStride
                
                for (x in 0 until width) {
                    val yOffset = yRowOffset + x * yPixelStride
                    val uvOffset = uvRowOffset + (x / 2) * uPixelStride
                    
                    val yVal = (yArray[yOffset].toInt() and 0xFF)
                    val uVal = (uArray[uvOffset].toInt() and 0xFF)
                    val vVal = if (vArray != null) {
                        val vOffset = (y / 2) * vRowStride + (x / 2) * vPixelStride
                        (vArray[vOffset].toInt() and 0xFF)
                    } else {
                        // If no separate V plane, assume U and V are interleaved
                        val vOffset = uvOffset + (if (x % 2 == 0) 0 else 1)
                        if (vOffset < uArray.size) (uArray[vOffset].toInt() and 0xFF) else 128
                    }
                    
                    // YUV to RGB conversion (ITU-R BT.601)
                    val c = yVal - 16
                    val d = uVal - 128
                    val e = vVal - 128
                    
                    val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                    val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                    val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
                    
                    pixels[pixelIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            // Rotate bitmap if needed (camera might be rotated)
            return if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                bitmap.recycle()
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error converting ImageProxy to Bitmap: ${e.message}", e)
            return null
        }
    }
    
    private fun detectObjects(bitmap: Bitmap): List<Detection> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
        
        // Preprocess image
        inputBuffer.rewind()
        val intValues = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
        
        var pixel = 0
        for (i in 0 until modelInputSize) {
            for (j in 0 until modelInputSize) {
                val pixelValue = intValues[pixel++]
                inputBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
            }
        }
        
        // Run inference
        // YOLOv8 TFLite outputs [1, 84, 8400] format
        val output = outputBuffer ?: return emptyList()
        interpreter?.run(inputBuffer, output)
        
        // Post-process results
        // output format: [1, 84, 8400] where:
        // - First dimension is batch (always 1)
        // - Second dimension is features: [0-3] = bbox, [4-83] = class scores
        // - Third dimension is number of detections (8400)
        val detections = mutableListOf<Detection>()
        val batch = output[0] // Get first (and only) batch
        val numDetections = batch[0].size
        
        for (i in 0 until numDetections) {
            // Get bbox coordinates from batch[0-3][i]
            val xCenter = batch[0][i]
            val yCenter = batch[1][i]
            val width = batch[2][i]
            val height = batch[3][i]
            
            // Find class with highest score from batch[4-83][i]
            var maxClass = 0
            var maxScore = batch[4][i]
            for (j in 1 until numClasses) {
                val score = batch[4 + j][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = j
                }
            }
            
            // Apply confidence threshold
            if (maxScore < confidenceThreshold) continue
            
            // Convert to pixel coordinates (YOLOv8 outputs normalized coordinates)
            val scaleX = bitmap.width.toFloat() / modelInputSize
            val scaleY = bitmap.height.toFloat() / modelInputSize
            
            val left = (xCenter - width / 2) * scaleX
            val top = (yCenter - height / 2) * scaleY
            val right = (xCenter + width / 2) * scaleX
            val bottom = (yCenter + height / 2) * scaleY
            
            detections.add(
                Detection(
                    classNames[maxClass],
                    maxScore,
                    RectF(left, top, right, bottom)
                )
            )
        }
        
        // Apply Non-Maximum Suppression
        return applyNMS(detections)
    }
    
    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val suppressed = BooleanArray(detections.size)
        
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            selected.add(sorted[i])
            
            for (j in (i + 1) until sorted.size) {
                if (suppressed[j]) continue
                val iou = calculateIoU(sorted[i].box, sorted[j].box)
                if (iou > nmsThreshold) {
                    suppressed[j] = true
                }
            }
        }
        
        return selected
    }
    
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)
        
        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) {
            return 0f
        }
        
        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea
        
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
    
    fun close() {
        interpreter?.close()
    }
    
    data class Detection(
        val className: String,
        val confidence: Float,
        val box: RectF
    )
}

