package ru.example.roadalert.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.AssociationInfo
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.example.roadalert.R
import ru.example.roadalert.drive.DriveForegroundService
import ru.example.roadalert.ui.MainActivity
import ru.example.roadalert.util.AppLog

/**
 * Автозапуск при подключении к автомобилю (ТЗ §14).
 *
 * Используется официальный CompanionDeviceManager. Бесконечного сканирования
 * Bluetooth, скрытых сервисов 24/7 и accessibility-обходов здесь нет и не будет:
 * если система не даёт стартовать автоматически, показываем уведомление в один тап.
 */
class CarConnectionManager(private val context: Context) {

    private val companionDeviceManager: CompanionDeviceManager? =
        ContextCompat.getSystemService(context, CompanionDeviceManager::class.java)

    val isSupported: Boolean get() = companionDeviceManager != null

    val hasBluetoothPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Показывает системный диалог выбора автомобиля.
     * @param onReady системный IntentSender, который Activity должна запустить
     */
    fun requestAssociation(
        onReady: (IntentSender) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val manager = companionDeviceManager ?: run {
            onFailure("CompanionDeviceManager недоступен на этом устройстве")
            return
        }

        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()

        runCatching {
            manager.associate(
                request,
                object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(intentSender: IntentSender) {
                        onReady(intentSender)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onFailure(error: CharSequence?) {
                        AppLog.event("COMPANION_ASSOCIATION_FAILED")
                        onFailure(error?.toString() ?: "Не удалось выбрать устройство")
                    }
                },
                null,
            )
        }.onFailure {
            AppLog.event("COMPANION_ASSOCIATE_EXCEPTION", "reason" to it.message)
            onFailure(it.message ?: "Ошибка Bluetooth")
        }
    }

    /** Разбирает результат системного диалога выбора устройства. */
    @SuppressLint("MissingPermission") // hasBluetoothPermission проверяется прямо перед обращением к имени
    fun extractDevice(data: Intent?): Pair<String, String>? {
        if (data == null) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val info = data.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java,
            )
            if (info != null) {
                val name = info.displayName?.toString() ?: DEFAULT_CAR_NAME
                return name to info.deviceMacAddress?.toString().orEmpty()
            }
        }

        @Suppress("DEPRECATION")
        val device = data.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
            ?: return null
        val name = runCatching {
            if (hasBluetoothPermission) device.name else null
        }.getOrNull() ?: DEFAULT_CAR_NAME
        return name to device.address
    }

    /**
     * Реакция на подключение выбранного автомобиля.
     *
     * Сначала пробуем официальный путь автозапуска, доступный companion-приложениям;
     * если система его не разрешает — показываем уведомление «запустить в один тап».
     */
    fun onCarConnected() {
        val started = tryStartTripDirectly()
        if (!started) showOneTapNotification()
    }

    private fun tryStartTripDirectly(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = ContextCompat.checkSelfPermission(
            context,
            "android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND",
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        return runCatching {
            DriveForegroundService.start(context)
            AppLog.event("CAR_AUTOSTART_DIRECT")
            true
        }.getOrElse {
            // ForegroundServiceStartNotAllowedException и подобные — это нормальный
            // отказ системы, а не повод пытаться в цикле.
            AppLog.event("CAR_AUTOSTART_DENIED", "reason" to it::class.java.simpleName)
            false
        }
    }

    private fun showOneTapNotification() {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_car),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_START_TRIP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_car_title))
            .setContentText(context.getString(R.string.notification_car_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        AppLog.event("CAR_ONE_TAP_NOTIFICATION")
    }

    companion object {
        const val DEFAULT_CAR_NAME = "Автомобиль"

        private const val CHANNEL_ID = "car_connection"
        private const val NOTIFICATION_ID = 1002
    }
}
