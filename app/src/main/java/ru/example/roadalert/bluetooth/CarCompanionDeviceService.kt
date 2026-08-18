package ru.example.roadalert.bluetooth

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.example.roadalert.app.RoadAlertApplication
import ru.example.roadalert.util.AppLog

/**
 * Системный сервис, который Android сам будит при появлении привязанного
 * автомобиля (ТЗ §14). Своего фонового сканирования приложение не ведёт.
 */
@RequiresApi(Build.VERSION_CODES.S)
class CarCompanionDeviceService : CompanionDeviceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @SuppressLint("MissingPermission")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        AppLog.event("CAR_DEVICE_APPEARED")
        val container = (application as RoadAlertApplication).container
        scope.launch {
            val settings = container.settingsRepository.settings.first()
            if (!settings.autoStartByBluetooth) return@launch

            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                associationInfo.deviceMacAddress?.toString()
            } else {
                null
            }
            val expected = settings.carDeviceAddress
            // Привязок может быть несколько; реагируем только на выбранный автомобиль.
            if (expected != null && address != null && !expected.equals(address, ignoreCase = true)) {
                return@launch
            }
            CarConnectionManager(applicationContext).onCarConnected()
        }
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        AppLog.event("CAR_DEVICE_DISAPPEARED")
        // Поездку сознательно не останавливаем: Bluetooth может отвалиться на ходу.
    }
}
