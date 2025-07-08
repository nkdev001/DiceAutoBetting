package com.example.diceautobetting.models

data class BettingState(
    val currentBet: Int = 10,                // Текущая ставка
    val baseBet: Int = 10,                   // Базовая ставка
    val lossCount: Int = 0,                  // Количество проигрышей подряд
    val maxLossCount: Int = 10,              // Максимальное количество удвоений
    val selectedColor: DiceColor = DiceColor.NONE, // Выбранный цвет
    val isActive: Boolean = false,            // Активна ли автоставка
    val totalWins: Int = 0,                   // Общее количество выигрышей
    val totalLosses: Int = 0,                 // Общее количество проигрышей
    val totalProfit: Int = 0                  // Общая прибыль/убыток
)

enum class DiceColor {
    RED,
    ORANGE,
    NONE
}

data class DiceResult(
    val redDots: Int,      // Количество точек на красном кубике
    val orangeDots: Int,   // Количество точек на оранжевом кубике
    val timestamp: Long = System.currentTimeMillis()
)

data class BetResult(
    val won: Boolean,      // Выиграна ли ставка
    val isDraw: Boolean,   // Была ли ничья
    val amount: Int,       // Сумма ставки
    val profit: Int        // Прибыль/убыток
)

// Настройки приложения
data class AppSettings(
    val captureRegion: CaptureRegion? = null,    // Область захвата для кубиков
    val bettingRegion: BettingRegion? = null,    // Область для выбора ставки
    val checkInterval: Long = 6000,               // Интервал проверки в мс
    val maxLossCount: Int = 10,                   // Максимальное количество удвоений
    val baseBetAmount: Int = 10                   // Базовая сумма ставки
)

// Область захвата экрана
data class CaptureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

// Область для клика по ставке
data class BettingRegion(
    val redX: Int,
    val redY: Int,
    val orangeX: Int,
    val orangeY: Int,
    val drawX: Int,
    val drawY: Int,
    val betButtonX: Int,
    val betButtonY: Int
)