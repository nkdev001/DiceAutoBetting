package com.example.diceautobetting

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.diceautobetting.data.SettingsRepository
import com.example.diceautobetting.models.DiceColor
import com.example.diceautobetting.services.AutoClickerService
import com.example.diceautobetting.services.OverlayService
import com.example.diceautobetting.services.ScreenCaptureService
import com.example.diceautobetting.ui.theme.DiceAutoBettingTheme
import com.example.diceautobetting.utils.BettingManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    private lateinit var bettingManager: BettingManager
    private lateinit var settingsRepository: SettingsRepository

    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                ScreenCaptureService.resultCode = result.resultCode
                ScreenCaptureService.resultData = data
                startScreenCaptureService()
            }
        } else {
            Toast.makeText(this, "Разрешение на захват экрана не получено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bettingManager = BettingManager(this)
        settingsRepository = SettingsRepository(this)

        setupCallbacks()
        checkPermissions()

        setContent {
            DiceAutoBettingTheme {
                MainScreen()
            }
        }
    }

    private fun setupCallbacks() {
        // Callback для результатов распознавания кубиков
        ScreenCaptureService.onDiceResultCallback = { diceResult ->
            lifecycleScope.launch {
                val betResult = bettingManager.processDiceResult(diceResult)

                betResult?.let { result ->
                    if (bettingManager.getCurrentState().isActive) {
                        // Выполняем следующую ставку
                        performAutoBet()
                    }
                }
            }
        }

        // Callback для выбора области захвата
        OverlayService.onRegionSelected = { region ->
            lifecycleScope.launch {
                settingsRepository.saveCaptureRegion(region)
                ScreenCaptureService.captureRegion = region
            }
        }

        // Callback для выбора цвета
        OverlayService.onColorSelected = { color ->
            bettingManager.updateSettings(selectedColor = color)
        }
    }

    private fun checkPermissions() {
        // Проверка разрешения на наложение окон
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }

        // Проверка доступности Accessibility Service
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(
                this,
                "Пожалуйста, включите службу доступности для этого приложения",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AutoClickerService.instance != null
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCapturePermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startScreenCaptureService() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = "SHOW_CONTROL_PANEL"
        }
        startService(intent)
    }

    private fun performAutoBet() {
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                settings.bettingRegion?.let { region ->
                    val state = bettingManager.getCurrentState()

                    AutoClickerService.instance?.performBettingSequence(
                        bettingRegion = region,
                        selectedColor = state.selectedColor,
                        betAmount = state.currentBet
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        val bettingState by bettingManager.bettingState.collectAsState()
        val settings by settingsRepository.settingsFlow.collectAsState(initial = null)

        var baseBet by remember { mutableStateOf("10") }
        var maxLossCount by remember { mutableStateOf("10") }
        var checkInterval by remember { mutableStateOf("6") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dice Auto Betting") }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Статус карточка
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Статус",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Активно:")
                            Text(
                                text = if (bettingState.isActive) "Да" else "Нет",
                                color = if (bettingState.isActive)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Выбранный цвет:")
                            Text(
                                text = when (bettingState.selectedColor) {
                                    DiceColor.RED -> "Красный"
                                    DiceColor.ORANGE -> "Оранжевый"
                                    DiceColor.NONE -> "Не выбран"
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Текущая ставка:")
                            Text("${bettingState.currentBet} руб.")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Проигрышей подряд:")
                            Text("${bettingState.lossCount}")
                        }
                    }
                }

                // Настройки карточка
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Настройки",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        OutlinedTextField(
                            value = baseBet,
                            onValueChange = { baseBet = it },
                            label = { Text("Базовая ставка (руб)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = maxLossCount,
                            onValueChange = { maxLossCount = it },
                            label = { Text("Макс. количество удвоений") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = checkInterval,
                            onValueChange = { checkInterval = it },
                            label = { Text("Интервал проверки (сек)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    val bet = baseBet.toIntOrNull() ?: 10
                                    val loss = maxLossCount.toIntOrNull() ?: 10
                                    val interval = (checkInterval.toLongOrNull() ?: 6) * 1000

                                    settingsRepository.saveBaseBetAmount(bet)
                                    settingsRepository.saveMaxLossCount(loss)
                                    settingsRepository.saveCheckInterval(interval)

                                    bettingManager.updateSettings(
                                        baseBet = bet,
                                        maxLossCount = loss
                                    )

                                    ScreenCaptureService.checkInterval = interval

                                    Toast.makeText(
                                        context,
                                        "Настройки сохранены",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Сохранить настройки")
                        }
                    }
                }

                // Статистика карточка
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Статистика",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Выигрышей:")
                            Text("${bettingState.totalWins}")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Проигрышей:")
                            Text("${bettingState.totalLosses}")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Прибыль/убыток:")
                            Text(
                                text = "${bettingState.totalProfit} руб.",
                                color = if (bettingState.totalProfit >= 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                bettingManager.resetStatistics()
                                Toast.makeText(
                                    context,
                                    "Статистика сброшена",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Сбросить статистику")
                        }
                    }
                }

                // Кнопки управления
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            requestScreenCapturePermission()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Запустить захват")
                    }

                    Button(
                        onClick = {
                            startOverlayService()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Показать панель")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (bettingState.selectedColor != DiceColor.NONE &&
                                settings?.captureRegion != null &&
                                settings?.bettingRegion != null
                            ) {
                                bettingManager.startBetting()
                                Toast.makeText(
                                    context,
                                    "Автоставка запущена",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Сначала настройте все параметры",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !bettingState.isActive
                    ) {
                        Text("Старт")
                    }

                    Button(
                        onClick = {
                            bettingManager.stopBetting()
                            Toast.makeText(
                                context,
                                "Автоставка остановлена",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = bettingState.isActive
                    ) {
                        Text("Стоп")
                    }
                }
            }
        }
    }
}