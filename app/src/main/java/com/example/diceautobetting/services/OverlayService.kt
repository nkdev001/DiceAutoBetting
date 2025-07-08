package com.example.diceautobetting.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import com.example.diceautobetting.R
import com.example.diceautobetting.models.BettingRegion
import com.example.diceautobetting.models.CaptureRegion
import com.example.diceautobetting.models.DiceColor
import kotlinx.coroutines.*

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        var isRunning = false

        // Callbacks
        var onRegionSelected: ((CaptureRegion) -> Unit)? = null
        var onBettingRegionSelected: ((BettingRegion) -> Unit)? = null
        var onColorSelected: ((DiceColor) -> Unit)? = null
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var controlPanelView: View? = null
    private var regionSelectorView: View? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_CONTROL_PANEL" -> showControlPanel()
            "SHOW_REGION_SELECTOR" -> showRegionSelector()
            "HIDE_ALL" -> hideAllOverlays()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        hideAllOverlays()
        serviceScope.cancel()
    }

    private fun showControlPanel() {
        if (controlPanelView != null) return

        val inflater = LayoutInflater.from(this)
        controlPanelView = inflater.inflate(R.layout.control_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        // Настройка перетаскивания
        setupDraggable(controlPanelView!!, params)

        // Настройка кнопок
        controlPanelView?.apply {
            findViewById<Button>(R.id.btnSelectRegion)?.setOnClickListener {
                showRegionSelector()
            }

            findViewById<Button>(R.id.btnSelectRed)?.setOnClickListener {
                onColorSelected?.invoke(DiceColor.RED)
                updateColorSelection(DiceColor.RED)
            }

            findViewById<Button>(R.id.btnSelectOrange)?.setOnClickListener {
                onColorSelected?.invoke(DiceColor.ORANGE)
                updateColorSelection(DiceColor.ORANGE)
            }

            findViewById<Button>(R.id.btnMinimize)?.setOnClickListener {
                controlPanelView?.visibility = View.GONE
                showFloatingButton()
            }
        }

        windowManager.addView(controlPanelView, params)
    }

    private fun showFloatingButton() {
        if (overlayView != null) return

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 200
        }

        setupDraggable(overlayView!!, params)

        overlayView?.setOnClickListener {
            overlayView?.let { windowManager.removeView(it) }
            overlayView = null
            controlPanelView?.visibility = View.VISIBLE
        }

        windowManager.addView(overlayView, params)
    }

    private fun showRegionSelector() {
        if (regionSelectorView != null) return

        regionSelectorView = RegionSelectorView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(regionSelectorView, params)
    }

    private fun setupDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateColorSelection(color: DiceColor) {
        controlPanelView?.apply {
            findViewById<Button>(R.id.btnSelectRed)?.apply {
                alpha = if (color == DiceColor.RED) 1.0f else 0.5f
            }
            findViewById<Button>(R.id.btnSelectOrange)?.apply {
                alpha = if (color == DiceColor.ORANGE) 1.0f else 0.5f
            }
        }
    }

    private fun hideAllOverlays() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        controlPanelView?.let {
            windowManager.removeView(it)
            controlPanelView = null
        }
        regionSelectorView?.let {
            windowManager.removeView(it)
            regionSelectorView = null
        }
    }

    // Внутренний класс для выбора области
    inner class RegionSelectorView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val fillPaint = Paint().apply {
            color = Color.argb(50, 255, 0, 0)
            style = Paint.Style.FILL
        }

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var isSelecting = false

        init {
            setBackgroundColor(Color.argb(100, 0, 0, 0))
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    isSelecting = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSelecting) {
                        endX = event.x
                        endY = event.y
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isSelecting) {
                        isSelecting = false

                        // Создаем регион
                        val left = minOf(startX, endX).toInt()
                        val top = minOf(startY, endY).toInt()
                        val right = maxOf(startX, endX).toInt()
                        val bottom = maxOf(startY, endY).toInt()

                        val region = CaptureRegion(
                            x = left,
                            y = top,
                            width = right - left,
                            height = bottom - top
                        )

                        onRegionSelected?.invoke(region)

                        // Убираем селектор
                        regionSelectorView?.let { windowManager.removeView(it) }
                        regionSelectorView = null
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (isSelecting) {
                val left = minOf(startX, endX)
                val top = minOf(startY, endY)
                val right = maxOf(startX, endX)
                val bottom = maxOf(startY, endY)

                canvas.drawRect(left, top, right, bottom, fillPaint)
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }
    }
}