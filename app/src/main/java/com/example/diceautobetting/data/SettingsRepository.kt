package com.example.diceautobetting.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.diceautobetting.models.AppSettings
import com.example.diceautobetting.models.BettingRegion
import com.example.diceautobetting.models.CaptureRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        // Keys для сохранения настроек
        private val CAPTURE_REGION_X = intPreferencesKey("capture_region_x")
        private val CAPTURE_REGION_Y = intPreferencesKey("capture_region_y")
        private val CAPTURE_REGION_WIDTH = intPreferencesKey("capture_region_width")
        private val CAPTURE_REGION_HEIGHT = intPreferencesKey("capture_region_height")

        private val BETTING_RED_X = intPreferencesKey("betting_red_x")
        private val BETTING_RED_Y = intPreferencesKey("betting_red_y")
        private val BETTING_ORANGE_X = intPreferencesKey("betting_orange_x")
        private val BETTING_ORANGE_Y = intPreferencesKey("betting_orange_y")
        private val BETTING_DRAW_X = intPreferencesKey("betting_draw_x")
        private val BETTING_DRAW_Y = intPreferencesKey("betting_draw_y")
        private val BETTING_BUTTON_X = intPreferencesKey("betting_button_x")
        private val BETTING_BUTTON_Y = intPreferencesKey("betting_button_y")

        private val CHECK_INTERVAL = longPreferencesKey("check_interval")
        private val MAX_LOSS_COUNT = intPreferencesKey("max_loss_count")
        private val BASE_BET_AMOUNT = intPreferencesKey("base_bet_amount")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val captureRegion = if (
                preferences.contains(CAPTURE_REGION_X) &&
                preferences.contains(CAPTURE_REGION_Y) &&
                preferences.contains(CAPTURE_REGION_WIDTH) &&
                preferences.contains(CAPTURE_REGION_HEIGHT)
            ) {
                CaptureRegion(
                    x = preferences[CAPTURE_REGION_X] ?: 0,
                    y = preferences[CAPTURE_REGION_Y] ?: 0,
                    width = preferences[CAPTURE_REGION_WIDTH] ?: 0,
                    height = preferences[CAPTURE_REGION_HEIGHT] ?: 0
                )
            } else null

            val bettingRegion = if (
                preferences.contains(BETTING_RED_X) &&
                preferences.contains(BETTING_RED_Y) &&
                preferences.contains(BETTING_ORANGE_X) &&
                preferences.contains(BETTING_ORANGE_Y) &&
                preferences.contains(BETTING_BUTTON_X) &&
                preferences.contains(BETTING_BUTTON_Y)
            ) {
                BettingRegion(
                    redX = preferences[BETTING_RED_X] ?: 0,
                    redY = preferences[BETTING_RED_Y] ?: 0,
                    orangeX = preferences[BETTING_ORANGE_X] ?: 0,
                    orangeY = preferences[BETTING_ORANGE_Y] ?: 0,
                    drawX = preferences[BETTING_DRAW_X] ?: 0,
                    drawY = preferences[BETTING_DRAW_Y] ?: 0,
                    betButtonX = preferences[BETTING_BUTTON_X] ?: 0,
                    betButtonY = preferences[BETTING_BUTTON_Y] ?: 0
                )
            } else null

            AppSettings(
                captureRegion = captureRegion,
                bettingRegion = bettingRegion,
                checkInterval = preferences[CHECK_INTERVAL] ?: 6000L,
                maxLossCount = preferences[MAX_LOSS_COUNT] ?: 10,
                baseBetAmount = preferences[BASE_BET_AMOUNT] ?: 10
            )
        }

    suspend fun saveCaptureRegion(region: CaptureRegion) {
        context.dataStore.edit { preferences ->
            preferences[CAPTURE_REGION_X] = region.x
            preferences[CAPTURE_REGION_Y] = region.y
            preferences[CAPTURE_REGION_WIDTH] = region.width
            preferences[CAPTURE_REGION_HEIGHT] = region.height
        }
    }

    suspend fun saveBettingRegion(region: BettingRegion) {
        context.dataStore.edit { preferences ->
            preferences[BETTING_RED_X] = region.redX
            preferences[BETTING_RED_Y] = region.redY
            preferences[BETTING_ORANGE_X] = region.orangeX
            preferences[BETTING_ORANGE_Y] = region.orangeY
            preferences[BETTING_DRAW_X] = region.drawX
            preferences[BETTING_DRAW_Y] = region.drawY
            preferences[BETTING_BUTTON_X] = region.betButtonX
            preferences[BETTING_BUTTON_Y] = region.betButtonY
        }
    }

    suspend fun saveCheckInterval(interval: Long) {
        context.dataStore.edit { preferences ->
            preferences[CHECK_INTERVAL] = interval
        }
    }

    suspend fun saveMaxLossCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_LOSS_COUNT] = count
        }
    }

    suspend fun saveBaseBetAmount(amount: Int) {
        context.dataStore.edit { preferences ->
            preferences[BASE_BET_AMOUNT] = amount
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}