package ru.example.roadalert.ui.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.example.roadalert.ui.components.SectionCard

/**
 * Инструменты разработчика (ТЗ §47). В release-сборке экран недоступен:
 * BuildConfig.DEVELOPER_MENU там равен false, а verifyReleaseConfig это проверяет.
 */
@Composable
fun DeveloperScreen(
    isSimulationRunning: Boolean,
    onReplayRoute: () -> Unit,
    onStopSimulation: () -> Unit,
    onFakeCameraAhead: () -> Unit,
    onFakeCameraBehind: () -> Unit,
    onOpenAdsDebugPanel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Developer tools", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Симулятор маршрута") {
            Text(
                "Подаёт синтетические координаты в детектор, не подменяя системный " +
                    "LocationManager. Реальную поездку не заменяет.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = if (isSimulationRunning) onStopSimulation else onReplayRoute,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSimulationRunning) "Остановить симуляцию" else "Replay test route")
            }
        }

        SectionCard("Тестовые камеры") {
            OutlinedButton(onClick = onFakeCameraAhead, modifier = Modifier.fillMaxWidth()) {
                Text("Fake camera ahead")
            }
            OutlinedButton(onClick = onFakeCameraBehind, modifier = Modifier.fillMaxWidth()) {
                Text("Fake camera behind")
            }
        }

        SectionCard("Реклама") {
            OutlinedButton(onClick = onOpenAdsDebugPanel, modifier = Modifier.fillMaxWidth()) {
                Text("Open Yandex Ads Debug Panel")
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}
