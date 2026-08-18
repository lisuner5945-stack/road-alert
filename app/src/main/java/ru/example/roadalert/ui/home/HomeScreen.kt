package ru.example.roadalert.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.example.roadalert.domain.model.GpsStatus
import ru.example.roadalert.ui.components.SectionCard
import ru.example.roadalert.ui.components.StatusRow
import ru.example.roadalert.ui.theme.AlertAmber
import ru.example.roadalert.ui.theme.AlertRed
import ru.example.roadalert.ui.theme.SafeGreen

data class HomeUiState(
    val databaseStatus: String,
    val databaseReady: Boolean,
    val cameraCount: Int,
    val gpsStatus: GpsStatus,
    val autoStartDeviceName: String?,
    val lastUpdateCheck: String?,
)

/**
 * Главный экран до поездки: статус, одна большая кнопка старта, вход в настройки.
 * Только здесь допустим рекламный баннер (ТЗ §25).
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartTrip: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    developerMenuAvailable: Boolean = false,
    onOpenDeveloper: () -> Unit = {},
    adBannerSlot: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ROAD ALERT", style = MaterialTheme.typography.displaySmall)

        SectionCard(title = null) {
            StatusRow(
                label = "База камер",
                value = state.databaseStatus,
                indicatorColor = if (state.databaseReady) SafeGreen else AlertAmber,
            )
            StatusRow(
                label = "GPS",
                value = gpsStatusText(state.gpsStatus),
                indicatorColor = gpsStatusColor(state.gpsStatus),
            )
            if (state.cameraCount > 0) {
                StatusRow(
                    label = "Камер в базе",
                    value = state.cameraCount.toString(),
                    indicatorColor = SafeGreen,
                )
            }
            if (state.lastUpdateCheck != null) {
                StatusRow(
                    label = "Проверка обновления",
                    value = state.lastUpdateCheck,
                    indicatorColor = Color(0xFF6B7785),
                )
            }
        }

        Button(
            onClick = onStartTrip,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen, contentColor = Color.White),
        ) {
            Text("НАЧАТЬ ПОЕЗДКУ", style = MaterialTheme.typography.headlineMedium)
        }

        if (state.autoStartDeviceName != null) {
            Text(
                "Автозапуск: ${state.autoStartDeviceName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                Text("Настройки")
            }
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.weight(1f)) {
                Text("О данных")
            }
        }

        if (developerMenuAvailable) {
            OutlinedButton(onClick = onOpenDeveloper, modifier = Modifier.fillMaxWidth()) {
                Text("Developer tools")
            }
        }

        Text(
            "Приложение является вспомогательным средством. Соблюдайте ПДД и дорожные знаки. " +
                "Не взаимодействуйте с телефоном во время движения.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        adBannerSlot()
    }
}

private fun gpsStatusText(status: GpsStatus): String = when (status) {
    GpsStatus.NO_PERMISSION -> "нет разрешения"
    GpsStatus.DISABLED -> "выключен"
    GpsStatus.WAITING -> "ожидание сигнала"
    GpsStatus.READY -> "готов"
    GpsStatus.APPROXIMATE_ONLY -> "приблизительный"
}

private fun gpsStatusColor(status: GpsStatus): Color = when (status) {
    GpsStatus.READY -> SafeGreen
    GpsStatus.WAITING, GpsStatus.APPROXIMATE_ONLY -> AlertAmber
    GpsStatus.NO_PERMISSION, GpsStatus.DISABLED -> AlertRed
}
