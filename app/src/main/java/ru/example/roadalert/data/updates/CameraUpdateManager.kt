package ru.example.roadalert.data.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.example.roadalert.data.camera.CameraRepository
import ru.example.roadalert.data.database.DatabaseMetaEntity
import ru.example.roadalert.util.AppLog
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Что произошло при попытке обновления базы. */
sealed interface UpdateResult {
    data class Updated(val cameraCount: Int, val version: String) : UpdateResult
    data object AlreadyUpToDate : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Безопасное обновление базы камер (ТЗ §11).
 *
 * Ключевое правило: рабочая база удаляется только после того, как новая
 * скачана, прошла проверку SHA-256, разобрана и провалидирована.
 * Любая ошибка на любом шаге — старая база остаётся нетронутой.
 */
class CameraUpdateManager(
    private val context: Context,
    private val repository: CameraRepository,
    private val baseUrl: String,
    private val parser: CameraDatabaseParser = CameraDatabaseParser(),
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun checkAndUpdate(force: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        runCatching { performUpdate(force) }
            .getOrElse { error ->
                AppLog.event("DB_UPDATE_FAILED", "reason" to (error.message ?: "unknown"))
                UpdateResult.Failed(error.message ?: "неизвестная ошибка")
            }
    }

    private suspend fun performUpdate(force: Boolean): UpdateResult {
        if (!baseUrl.startsWith("https://")) {
            return UpdateResult.Failed("Источник базы должен использовать HTTPS")
        }

        val metadataJson = download(baseUrl + METADATA_FILE)
            ?: return UpdateResult.Failed("Не удалось скачать metadata.json")
        val metadata = parser.parseMetadata(metadataJson)
            ?: return UpdateResult.Failed("metadata.json повреждён")

        if (metadata.schemaVersion != CameraDatabaseSchema.SUPPORTED_SCHEMA_VERSION) {
            return UpdateResult.Failed("Схема базы ${metadata.schemaVersion} не поддерживается")
        }
        if (metadata.cameraCount <= 0) {
            return UpdateResult.Failed("В обновлении ноль камер")
        }

        val localMeta = repository.currentMeta()
        val alreadyCurrent = localMeta != null &&
            localMeta.databaseVersion == metadata.databaseVersion &&
            repository.isLoaded
        if (alreadyCurrent && !force) {
            AppLog.event("DB_UP_TO_DATE", "version" to metadata.databaseVersion)
            return UpdateResult.AlreadyUpToDate
        }

        val temporaryFile = File(context.cacheDir, TEMP_FILE)
        try {
            val downloadUrl = metadata.downloadUrl
                ?.takeIf { it.startsWith("https://") }
                ?: (baseUrl + DATABASE_FILE)

            if (!downloadToFile(downloadUrl, temporaryFile)) {
                return UpdateResult.Failed("Не удалось скачать базу камер")
            }

            val actualSha = sha256(temporaryFile)
            if (!actualSha.equals(metadata.sha256, ignoreCase = true)) {
                AppLog.event("DB_SHA_MISMATCH")
                return UpdateResult.Failed("SHA-256 не совпал: файл повреждён")
            }

            val rawJson = GZIPInputStream(temporaryFile.inputStream().buffered())
                .use { it.readBytes().decodeToString() }

            return when (val parsed = parser.parse(rawJson, System.currentTimeMillis())) {
                is ParseResult.Failure -> UpdateResult.Failed(parsed.reason)

                is ParseResult.Success -> {
                    val meta = DatabaseMetaEntity(
                        schemaVersion = parsed.schemaVersion,
                        databaseVersion = parsed.databaseVersion,
                        generatedAt = parsed.generatedAt,
                        cameraCount = parsed.cameras.size,
                        source = parsed.source,
                        license = parsed.license,
                        importedAt = System.currentTimeMillis(),
                    )
                    repository.replaceDatabase(parsed.cameras, meta).fold(
                        onSuccess = { count ->
                            UpdateResult.Updated(count, parsed.databaseVersion)
                        },
                        onFailure = { error ->
                            // Транзакция откатилась — рабочая база не пострадала.
                            UpdateResult.Failed("Импорт не удался: ${error.message}")
                        },
                    )
                }
            }
        } finally {
            temporaryFile.delete()
        }
    }

    private fun download(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }.getOrNull()

    private fun downloadToFile(url: String, target: File): Boolean = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return false
            val stream = response.body?.byteStream() ?: return false
            target.outputStream().use { output -> stream.copyTo(output) }
        }
        target.length() > 0
    }.getOrDefault(false)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {

        const val METADATA_FILE = "metadata.json"
        const val DATABASE_FILE = "camera_database.json.gz"
        private const val TEMP_FILE = "camera_database_download.gz"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}
