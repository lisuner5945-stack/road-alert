package ru.example.roadalert.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.example.roadalert.util.AppLog

/**
 * После перезагрузки восстанавливаем только расписание обновления базы.
 *
 * GPS-поездка после загрузки телефона НЕ запускается никогда (ТЗ §15).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        AppLog.event("BOOT_COMPLETED")
        runCatching { DatabaseUpdateWorker.schedule(context.applicationContext) }
    }
}
