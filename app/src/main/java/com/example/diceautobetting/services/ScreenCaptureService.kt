package com.example.diceautobetting.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.diceautobetting.MainActivity
import com.example.diceautobetting.R
import com.example.diceautobetting.models.CaptureRegion
import com.example.diceautobetting.utils.ImageProcessor
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ScreenCaptureChannel"

        var resultCode: Int = 0
        var resultData: Intent? = null
        var captureRegion: CaptureRegion? = null
        var checkInterval: Long = 6000

        // Callback для результатов
        var onDiceResultCallback: ((com.example.diceautobetting.models.DiceResult) -> Unit)? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val imageProcessor = ImageProcessor()

    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDensity = 0

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var captureJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Получаем параметры экрана
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        displayWidth = displayMetrics.widthPixels
        displayHeight = displayMetrics.heightPixels
        displayDensity = displayMetrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        if (resultData != null) {
            startScreenCapture()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopScreenCapture()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис для захвата экрана и распознавания кубиков"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dice Auto Betting")
            .setContentText("Сервис активен")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startScreenCapture() {
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData!!)

            createVirtualDisplay()
            startCaptureCycle()

            Log.d(TAG, "Screen capture started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture", e)
        }
    }

    private fun createVirtualDisplay() {
        imageReader = ImageReader.newInstance(
            displayWidth,
            displayHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            displayWidth,
            displayHeight,
            displayDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun startCaptureCycle() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive) {
                captureAndAnalyze()
                delay(checkInterval)
            }
        }
    }

    private suspend fun captureAndAnalyze() = withContext(Dispatchers.IO) {
        try {
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()

                // Обрезаем изображение до нужной области
                captureRegion?.let { region ->
                    val croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        region.x,
                        region.y,
                        region.width,
                        region.height
                    )

                    // Анализируем изображение
                    val diceResult = imageProcessor.analyzeDiceImage(croppedBitmap)

                    diceResult?.let { result ->
                        Log.d(TAG, "Dice result: Red=${result.redDots}, Orange=${result.orangeDots}")

                        // Вызываем callback в главном потоке
                        withContext(Dispatchers.Main) {
                            onDiceResultCallback?.invoke(result)
                        }
                    }

                    croppedBitmap.recycle()
                }

                bitmap.recycle()
            } else {

            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during capture and analyze", e)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * displayWidth

        val bitmap = Bitmap.createBitmap(
            displayWidth + rowPadding / pixelStride,
            displayHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return bitmap
    }

    private fun stopScreenCapture() {
        captureJob?.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()

        virtualDisplay = null
        mediaProjection = null
        imageReader = null
    }
}