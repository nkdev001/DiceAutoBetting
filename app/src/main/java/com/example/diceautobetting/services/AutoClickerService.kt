package com.example.diceautobetting.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.diceautobetting.models.BettingRegion
import com.example.diceautobetting.models.DiceColor
import kotlinx.coroutines.*

class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"
        private const val CLICK_DURATION = 100L
        private const val CLICK_DELAY = 500L

        var instance: AutoClickerService? = null

        // Callback для результатов
        var onClickResult: ((Boolean) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Мы не обрабатываем события, только используем для кликов
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    fun performBettingSequence(
        bettingRegion: BettingRegion,
        selectedColor: DiceColor,
        betAmount: Int
    ) {
        serviceScope.launch {
            try {
                // 1. Сначала кликаем на выбранный цвет
                val colorClicked = when (selectedColor) {
                    DiceColor.RED -> performClick(bettingRegion.redX, bettingRegion.redY)
                    DiceColor.ORANGE -> performClick(bettingRegion.orangeX, bettingRegion.orangeY)
                    else -> false
                }

                if (!colorClicked) {
                    Log.e(TAG, "Failed to click on color selection")
                    onClickResult?.invoke(false)
                    return@launch
                }

                delay(CLICK_DELAY)

                // 2. Выбираем сумму ставки
                val betClicked = selectBetAmount(betAmount)
                if (!betClicked) {
                    Log.e(TAG, "Failed to select bet amount")
                    onClickResult?.invoke(false)
                    return@launch
                }

                delay(CLICK_DELAY)

                // 3. Нажимаем кнопку "Заключить пари"
                val betButtonClicked = performClick(bettingRegion.betButtonX, bettingRegion.betButtonY)

                if (betButtonClicked) {
                    Log.d(TAG, "Betting sequence completed successfully")
                    onClickResult?.invoke(true)
                } else {
                    Log.e(TAG, "Failed to click bet button")
                    onClickResult?.invoke(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in betting sequence", e)
                onClickResult?.invoke(false)
            }
        }
    }

    private suspend fun performClick(x: Int, y: Int): Boolean = withContext(Dispatchers.Main) {
        try {
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, CLICK_DURATION))
                .build()

            val result = suspendCancellableCoroutine<Boolean> { continuation ->
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        continuation.resume(true) {}
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        continuation.resume(false) {}
                    }
                }, null)
            }

            Log.d(TAG, "Click at ($x, $y) - Result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click", e)
            false
        }
    }

    private suspend fun selectBetAmount(amount: Int): Boolean {
        // Координаты кнопок ставок (нужно будет настроить под конкретную игру)
        val betButtonCoordinates = mapOf(
            10 to Pair(100, 900),    // Координаты кнопки "10"
            50 to Pair(200, 900),    // Координаты кнопки "50"
            100 to Pair(300, 900),   // Координаты кнопки "100"
            500 to Pair(400, 900),   // Координаты кнопки "500"
            2500 to Pair(500, 900)   // Координаты кнопки "2500"
        )

        // Находим ближайшую доступную ставку
        val availableBet = when {
            amount <= 10 -> 10
            amount <= 50 -> 50
            amount <= 100 -> 100
            amount <= 500 -> 500
            else -> 2500
        }

        val coordinates = betButtonCoordinates[availableBet] ?: return false

        // Если текущая ставка больше максимальной, нажимаем кнопку удвоения
        if (amount > 2500) {
            // Сначала выбираем максимальную ставку
            val maxBetClicked = performClick(coordinates.first, coordinates.second)
            if (!maxBetClicked) return false

            delay(CLICK_DELAY)

            // Затем нажимаем кнопку x2 нужное количество раз
            val doublingCount = (amount / 2500)
            repeat(doublingCount - 1) {
                val doubleClicked = performDoubleClick()
                if (!doubleClicked) return false
                delay(200)
            }

            return true
        }

        return performClick(coordinates.first, coordinates.second)
    }

    private suspend fun performDoubleClick(): Boolean {
        // Координаты кнопки x2 (нужно настроить)
        return performClick(600, 900)
    }

    fun clickAtPosition(x: Int, y: Int) {
        serviceScope.launch {
            performClick(x, y)
        }
    }
}