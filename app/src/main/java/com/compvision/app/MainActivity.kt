package com.compvision.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.compvision.app.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    
    private lateinit var objectDetector: YOLODetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d("MainActivity", "onCreate: Starting initialization")
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            updateStatus("Инициализация приложения...")
            Log.d("MainActivity", "onCreate: Layout inflated")

            updateStatus("Создание потоков...")
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            Log.d("MainActivity", "onCreate: Initializing YOLO detector")
            updateStatus("Загрузка модели YOLO...")
            
            // Initialize YOLO detector with status callback and object count callback
            objectDetector = YOLODetector(
                this,
                binding.overlay,
                statusCallback = { status ->
                    runOnUiThread {
                        updateStatus(status)
                    }
                },
                objectCountCallback = { counts ->
                    runOnUiThread {
                        updateObjectCounts(counts)
                    }
                }
            )
            
            Log.d("MainActivity", "onCreate: YOLO detector initialized")
            
            if (allPermissionsGranted()) {
                Log.d("MainActivity", "onCreate: Permissions granted, starting camera")
                updateStatus("Запуск камеры...")
                startCamera()
            } else {
                Log.d("MainActivity", "onCreate: Requesting permissions")
                updateStatus("Запрос разрешений...")
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            if (::binding.isInitialized) {
                updateStatus("Ошибка: ${e.message}")
            }
            Toast.makeText(
                this,
                "Error initializing app: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
    
    private fun updateStatus(message: String) {
        try {
            binding.infoText.text = message
            binding.progressContainer.visibility = View.VISIBLE
            Log.d("MainActivity", "Status: $message")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating status: ${e.message}", e)
        }
    }
    
    private fun hideStatus() {
        try {
            binding.progressContainer.visibility = View.GONE
            Log.d("MainActivity", "Status hidden")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error hiding status: ${e.message}", e)
        }
    }
    
    private fun updateObjectCounts(counts: Map<String, Int>) {
        try {
            if (counts.isEmpty()) {
                binding.objectCountNumber.text = "0"
                binding.objectClassesContainer.visibility = View.GONE
            } else {
                val totalCount = counts.values.sum()
                binding.objectCountNumber.text = totalCount.toString()
                
                // Update object classes
                binding.objectClassesContainer.removeAllViews()
                if (counts.isNotEmpty()) {
                    binding.objectClassesContainer.visibility = View.VISIBLE
                    
                    counts.entries.sortedByDescending { it.value }.forEach { (className, count) ->
                        val textView = android.widget.TextView(this).apply {
                            text = "$className: $count"
                            setTextAppearance(android.R.style.TextAppearance_Small)
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                            textSize = 12f
                            val padding = (4 * resources.displayMetrics.density).toInt()
                            setPadding(0, padding, 0, padding)
                        }
                        binding.objectClassesContainer.addView(textView)
                    }
                } else {
                    binding.objectClassesContainer.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating object counts: ${e.message}", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                Log.d("MainActivity", "onRequestPermissionsResult: Permissions granted")
                updateStatus("Запуск камеры...")
                startCamera()
            } else {
                Log.w("MainActivity", "onRequestPermissionsResult: Permissions denied")
                updateStatus("Разрешения отклонены")
                Toast.makeText(
                    this,
                    getString(R.string.permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        Log.d("MainActivity", "startCamera: Getting camera provider")
        updateStatus("Получение доступа к камере...")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                Log.d("MainActivity", "startCamera: Camera provider obtained")
                updateStatus("Настройка камеры...")
                
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                    }

                Log.d("MainActivity", "startCamera: Preview created")
                
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, objectDetector)
                    }

                Log.d("MainActivity", "startCamera: Image analyzer created")
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                
                Log.d("MainActivity", "startCamera: Camera bound successfully")
                updateStatus("Готово!")
                
                // Hide status after a short delay
                binding.infoText.postDelayed({
                    hideStatus()
                }, 2000)
                
            } catch (exc: Exception) {
                Log.e("MainActivity", "startCamera: Camera binding failed: ${exc.message}", exc)
                updateStatus("Ошибка камеры: ${exc.message}")
                Toast.makeText(
                    this,
                    "Camera binding failed: ${exc.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            if (::objectDetector.isInitialized) {
                objectDetector.close()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}", e)
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}



