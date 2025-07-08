package com.example.diceautobetting.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.diceautobetting.models.DiceResult
import kotlin.math.abs
import kotlin.math.sqrt

class ImageProcessor {

    companion object {
        private const val TAG = "ImageProcessor"

        // Пороговые значения для определения цветов
        private const val RED_HUE_MIN = 0f
        private const val RED_HUE_MAX = 20f
        private const val RED_HUE_MIN2 = 340f
        private const val RED_HUE_MAX2 = 360f

        private const val ORANGE_HUE_MIN = 20f
        private const val ORANGE_HUE_MAX = 40f

        // Минимальная насыщенность для определения цвета
        private const val MIN_SATURATION = 0.3f

        // Размер области для поиска точек
        private const val DOT_SEARCH_RADIUS = 5
    }

    fun analyzeDiceImage(bitmap: Bitmap): DiceResult? {
        try {
            // Разделяем изображение на две части (левый и правый кубик)
            val halfWidth = bitmap.width / 2

            val leftDice = Bitmap.createBitmap(bitmap, 0, 0, halfWidth, bitmap.height)
            val rightDice = Bitmap.createBitmap(bitmap, halfWidth, 0, halfWidth, bitmap.height)

            // Определяем цвет каждого кубика
            val leftColor = determineDiceColor(leftDice)
            val rightColor = determineDiceColor(rightDice)

            Log.d(TAG, "Left dice color: $leftColor, Right dice color: $rightColor")

            // Подсчитываем точки на каждом кубике
            val leftDots = countDots(leftDice)
            val rightDots = countDots(rightDice)

            Log.d(TAG, "Left dots: $leftDots, Right dots: $rightDots")

            // Определяем какой кубик красный, а какой оранжевый
            return when {
                leftColor == DiceColor.RED && rightColor == DiceColor.ORANGE -> {
                    DiceResult(redDots = leftDots, orangeDots = rightDots)
                }
                leftColor == DiceColor.ORANGE && rightColor == DiceColor.RED -> {
                    DiceResult(redDots = rightDots, orangeDots = leftDots)
                }
                else -> {
                    Log.e(TAG, "Could not determine dice colors properly")
                    null
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing dice image", e)
            return null
        }
    }

    private fun determineDiceColor(diceBitmap: Bitmap): DiceColor {
        var redPixels = 0
        var orangePixels = 0

        // Анализируем центральную часть кубика
        val centerX = diceBitmap.width / 2
        val centerY = diceBitmap.height / 2
        val sampleRadius = minOf(diceBitmap.width, diceBitmap.height) / 3

        for (x in (centerX - sampleRadius)..(centerX + sampleRadius)) {
            for (y in (centerY - sampleRadius)..(centerY + sampleRadius)) {
                if (x < 0 || x >= diceBitmap.width || y < 0 || y >= diceBitmap.height) continue

                val pixel = diceBitmap.getPixel(x, y)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)

                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]

                // Проверяем только яркие и насыщенные пиксели
                if (saturation > MIN_SATURATION && value > 0.5f) {
                    when {
                        isRedHue(hue) -> redPixels++
                        isOrangeHue(hue) -> orangePixels++
                    }
                }
            }
        }

        Log.d(TAG, "Color analysis - Red pixels: $redPixels, Orange pixels: $orangePixels")

        return when {
            redPixels > orangePixels -> DiceColor.RED
            orangePixels > redPixels -> DiceColor.ORANGE
            else -> DiceColor.UNKNOWN
        }
    }

    private fun isRedHue(hue: Float): Boolean {
        return (hue >= RED_HUE_MIN && hue <= RED_HUE_MAX) ||
                (hue >= RED_HUE_MIN2 && hue <= RED_HUE_MAX2)
    }

    private fun isOrangeHue(hue: Float): Boolean {
        return hue >= ORANGE_HUE_MIN && hue <= ORANGE_HUE_MAX
    }

    private fun countDots(diceBitmap: Bitmap): Int {
        // Преобразуем в черно-белое изображение для лучшего распознавания точек
        val bwBitmap = convertToBlackAndWhite(diceBitmap)

        // Ищем белые круглые области (точки на кубике)
        val dots = findWhiteDots(bwBitmap)

        // Валидация результата (от 1 до 6 точек)
        return when {
            dots in 1..6 -> dots
            dots == 0 -> {
                Log.w(TAG, "No dots found, defaulting to 1")
                1
            }
            else -> {
                Log.w(TAG, "Invalid dot count: $dots, defaulting to 6")
                6
            }
        }
    }

    private fun convertToBlackAndWhite(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Находим средний уровень яркости
        var totalBrightness = 0.0
        var pixelCount = 0

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = getBrightness(pixel)
                totalBrightness += brightness
                pixelCount++
            }
        }

        val threshold = (totalBrightness / pixelCount * 0.7).toInt() // 70% от средней яркости

        // Применяем пороговое значение
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = getBrightness(pixel)

                val newColor = if (brightness > threshold) Color.WHITE else Color.BLACK
                bwBitmap.setPixel(x, y, newColor)
            }
        }

        return bwBitmap
    }

    private fun getBrightness(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun findWhiteDots(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val visited = Array(width) { BooleanArray(height) }
        var dotCount = 0

        // Минимальный размер точки
        val minDotSize = (width * height * 0.001).toInt() // 0.1% от площади изображения
        val maxDotSize = (width * height * 0.05).toInt()  // 5% от площади изображения

        for (x in 0 until width) {
            for (y in 0 until height) {
                if (!visited[x][y] && bitmap.getPixel(x, y) == Color.WHITE) {
                    val dotSize = floodFill(bitmap, visited, x, y)

                    if (dotSize in minDotSize..maxDotSize) {
                        dotCount++
                    }
                }
            }
        }

        return dotCount
    }

    private fun floodFill(bitmap: Bitmap, visited: Array<BooleanArray>, startX: Int, startY: Int): Int {
        val width = bitmap.width
        val height = bitmap.height
        val stack = mutableListOf(Pair(startX, startY))
        var size = 0

        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)

            if (x < 0 || x >= width || y < 0 || y >= height || visited[x][y]) {
                continue
            }

            if (bitmap.getPixel(x, y) != Color.WHITE) {
                continue
            }

            visited[x][y] = true
            size++

            // Добавляем соседние пиксели
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }

        return size
    }

    enum class DiceColor {
        RED, ORANGE, UNKNOWN
    }
}