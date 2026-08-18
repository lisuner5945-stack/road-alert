package ru.example.roadalert.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.example.roadalert.data.settings.AppSettings
import ru.example.roadalert.data.settings.DistanceProfile
import ru.example.roadalert.ui.components.SectionCard

/** Колбэки настроек — по одному на параметр, без «умных» абстракций. */
data class SettingsActions(
    val onVoiceAlerts: (Boolean) -> Unit,
    val onSoundSignal: (Boolean) -> Unit,
    val onVibration: (Boolean) -> Unit,
    val onAlertOnlyWhenSpeeding: (Boolean) -> Unit,
    val onSpeedTolerance: (Int) -> Unit,
    val onDistanceProfile: (DistanceProfile) -> Unit,
    val onOverlayEnabled: (Boolean) -> Unit,
    val onKeepScreenOn: (Boolean) -> Unit,
    val onAutoStartBluetooth: (Boolean) -> Unit,
    val onPickCarDevice: () -> Unit,
    val onAutoUpdateDatabase: (Boolean) -> Unit,
    val onCheckUpdateNow: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onBack: () -> Unit,
)

data class SettingsInfo(
    val appVersion: String,
    val databaseVersion: String,
    val cameraCount: Int,
    val lastUpdateCheck: String?,
    val updateInProgress: Boolean = false,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    info: SettingsInfo,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    adBannerSlot: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Предупреждения") {
            SwitchRow("Голосовые предупреждения", settings.voiceAlerts, actions.onVoiceAlerts)
            SwitchRow("Звуковой сигнал", settings.soundSignal, actions.onSoundSignal)
            SwitchRow("Вибрация", settings.vibration, actions.onVibration)
            SwitchRow(
                "Предупреждать только при превышении",
                settings.alertOnlyWhenSpeeding,
                actions.onAlertOnlyWhenSpeeding,
            )
            Text("Допуск превышения", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10).forEach { tolerance ->
                    FilterChip(
                        selected = settings.speedToleranceKmh == tolerance,
                        onClick = { actions.onSpeedTolerance(tolerance) },
                        label = { Text("+$tolerance км/ч") },
                    )
                }
            }
            Text("Дистанция предупреждения", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistanceProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = settings.distanceProfile == profile,
                        onClick = { actions.onDistanceProfile(profile) },
                        label = { Text(distanceProfileTitle(profile)) },
                    )
                }
            }
        }

        SectionCard("Экран") {
            SwitchRow("Overlay поверх навигатора", settings.overlayEnabled, actions.onOverlayEnabled)
            SwitchRow("Не гасить экран в поездке", settings.keepScreenOnInDrive, actions.onKeepScreenOn)
        }

        SectionCard("Автозапуск в машине") {
            SwitchRow("Запускать при подключении к авто", settings.autoStartByBluetooth, actions.onAutoStartBluetooth)
            Text(
                settings.carDeviceName?.let { "Выбранное устройство: $it" } ?: "Устройство не выбрано",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = actions.onPickCarDevice, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать автомобиль")
            }
        }

        SectionCard("База камер") {
            SwitchRow("Автообновление базы", settings.autoUpdateDatabase, actions.onAutoUpdateDatabase)
            Text("Версия базы: ${info.databaseVersion}", style = MaterialTheme.typography.bodyMedium)
            Text("Камер в базе: ${info.cameraCount}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Последняя проверка: ${info.lastUpdateCheck ?: "не выполнялась"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = actions.onCheckUpdateNow,
                enabled = !info.updateInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (info.updateInProgress) "Проверяем…" else "Проверить обновление сейчас")
            }
        }

        SectionCard("О приложении") {
            Text("Версия приложения: ${info.appVersion}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "О данных, Privacy Policy, OpenStreetMap",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions.onOpenAbout)
                    .padding(vertical = 6.dp),
            )
        }

        adBannerSlot()

        OutlinedButton(onClick = actions.onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun distanceProfileTitle(profile: DistanceProfile): String = when (profile) {
    DistanceProfile.EARLY -> "Ранняя"
    DistanceProfile.AUTO -> "Авто"
    DistanceProfile.LATE -> "Поздняя"
}
