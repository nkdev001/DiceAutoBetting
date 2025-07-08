package com.example.diceautobetting.utils

import android.content.Context
import android.util.Log
import com.example.diceautobetting.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BettingManager(private val context: Context) {

    companion object {
        private const val TAG = "BettingManager"
        private const val WIN_COEFFICIENT = 2.28
    }

    private val _bettingState = MutableStateFlow(BettingState())
    val bettingState: StateFlow<BettingState> = _bettingState.asStateFlow()

    private val _lastResult = MutableStateFlow<DiceResult?>(null)
    val lastResult: StateFlow<DiceResult?> = _lastResult.asStateFlow()

    fun updateSettings(
        baseBet: Int? = null,
        maxLossCount: Int? = null,
        selectedColor: DiceColor? = null
    ) {
        _bettingState.value = _bettingState.value.copy(
            baseBet = baseBet ?: _bettingState.value.baseBet,
            currentBet = baseBet ?: _bettingState.value.currentBet,
            maxLossCount = maxLossCount ?: _bettingState.value.maxLossCount,
            selectedColor = selectedColor ?: _bettingState.value.selectedColor
        )
    }

    fun startBetting() {
        _bettingState.value = _bettingState.value.copy(
            isActive = true,
            currentBet = _bettingState.value.baseBet,
            lossCount = 0
        )
        Log.d(TAG, "Betting started with base bet: ${_bettingState.value.baseBet}")
    }

    fun stopBetting() {
        _bettingState.value = _bettingState.value.copy(isActive = false)
        Log.d(TAG, "Betting stopped")
    }

    fun processDiceResult(diceResult: DiceResult): BetResult? {
        _lastResult.value = diceResult

        val state = _bettingState.value
        if (!state.isActive || state.selectedColor == DiceColor.NONE) {
            return null
        }

        val betResult = calculateBetResult(diceResult, state)
        updateStateAfterBet(betResult)

        Log.d(TAG, "Bet result: ${if (betResult.won) "WIN" else "LOSS"}, " +
                "Amount: ${betResult.amount}, Profit: ${betResult.profit}")

        return betResult
    }

    private fun calculateBetResult(diceResult: DiceResult, state: BettingState): BetResult {
        val isDraw = diceResult.redDots == diceResult.orangeDots
        val won = when {
            isDraw -> false // Ничья считается проигрышем
            state.selectedColor == DiceColor.RED -> diceResult.redDots > diceResult.orangeDots
            state.selectedColor == DiceColor.ORANGE -> diceResult.orangeDots > diceResult.redDots
            else -> false
        }

        val profit = if (won) {
            (state.currentBet * (WIN_COEFFICIENT - 1)).toInt()
        } else {
            -state.currentBet
        }

        return BetResult(
            won = won,
            isDraw = isDraw,
            amount = state.currentBet,
            profit = profit
        )
    }

    private fun updateStateAfterBet(betResult: BetResult) {
        val currentState = _bettingState.value

        if (betResult.won) {
            // Выигрыш - сбрасываем на базовую ставку
            _bettingState.value = currentState.copy(
                currentBet = currentState.baseBet,
                lossCount = 0,
                totalWins = currentState.totalWins + 1,
                totalProfit = currentState.totalProfit + betResult.profit
            )
        } else {
            // Проигрыш - удваиваем ставку если не достигли лимита
            val newLossCount = currentState.lossCount + 1

            if (newLossCount >= currentState.maxLossCount) {
                // Достигли максимума проигрышей - останавливаем
                _bettingState.value = currentState.copy(
                    isActive = false,
                    lossCount = newLossCount,
                    totalLosses = currentState.totalLosses + 1,
                    totalProfit = currentState.totalProfit + betResult.profit
                )
                Log.w(TAG, "Maximum loss count reached. Stopping betting.")
            } else {
                // Удваиваем ставку
                val newBet = currentState.currentBet * 2
                _bettingState.value = currentState.copy(
                    currentBet = newBet,
                    lossCount = newLossCount,
                    totalLosses = currentState.totalLosses + 1,
                    totalProfit = currentState.totalProfit + betResult.profit
                )
            }
        }
    }

    fun resetStatistics() {
        _bettingState.value = _bettingState.value.copy(
            totalWins = 0,
            totalLosses = 0,
            totalProfit = 0,
            lossCount = 0,
            currentBet = _bettingState.value.baseBet
        )
    }

    fun getCurrentState(): BettingState = _bettingState.value
}