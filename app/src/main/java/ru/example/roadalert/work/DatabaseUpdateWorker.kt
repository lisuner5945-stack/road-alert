package ru.example.roadalert.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.example.roadalert.app.RoadAlertApplication
import ru.example.roadalert.data.updates.UpdateResult
import ru.example.roadalert.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка обновлений базы (ТЗ §11).
 *
 * Только при наличии сети, не чаще раза в сутки. GPS ради обновления
 * не включается никогда.
 */
class DatabaseUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as RoadAlertApplication).container

        if (!container.settingsRepository.settings.first().autoUpdateDatabase) {
            AppLog.event("DB_UPDATE_SKIPPED_BY_SETTINGS")
            return Result.success()
        }

        return when (val result = container.cameraUpdateManager.checkAndUpdate()) {
            is UpdateResult.Updated -> {
                container.recordUpdateCheck()
                AppLog.event("DB_UPDATE_SUCCESS", "version" to result.version)
                Result.success()
            }

            UpdateResult.AlreadyUpToDate -> {
                container.recordUpdateCheck()
                Result.success()
            }

            is UpdateResult.Failed -> {
                container.recordUpdateCheck()
                AppLog.event("DB_UPDATE_FAILED", "reason" to result.reason)
                // Сеть могла просто отвалиться — пробуем позже, старая база работает.
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            }
        }
    }

    companion object {

        private const val UNIQUE_NAME = "camera-database-update"
        private const val MAX_ATTEMPTS = 3

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DatabaseUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            AppLog.event("DB_UPDATE_SCHEDULED")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
