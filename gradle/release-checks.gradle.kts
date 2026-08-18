/*
 * Release build protection (ТЗ §48).
 *
 * Задача verifyReleaseConfig выполняется ДО упаковки release-артефакта и
 * роняет сборку, если проект ещё не готов к публикации в RuStore.
 * На debug-сборку никак не влияет.
 */

val forbiddenPermissions = listOf(
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_CONTACTS",
    "android.permission.READ_CALL_LOG",
    "android.permission.RECORD_AUDIO",
    "android.permission.CAMERA",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
)

val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    group = "verification"
    description = "Проверяет, что release-сборка пригодна для публикации (ТЗ §48)."

    // Проверять нужно уже сгенерированные BuildConfig и merged manifest,
    // поэтому зависимости резолвятся лениво: на момент применения скрипта
    // задачи AGP ещё не созданы.
    dependsOn(
        java.util.concurrent.Callable {
            listOf("generateReleaseBuildConfig", "processReleaseMainManifest", "processReleaseManifest")
                .mapNotNull { name -> tasks.findByName(name) }
        },
    )

    val rootDirectory = rootProject.projectDir
    val buildDirectory = project.layout.buildDirectory.get().asFile

    doLast {
        val problems = mutableListOf<String>()

        // --- BuildConfig release ---
        val buildConfig = buildDirectory.resolve("generated/source/buildConfig/release")
            .walkTopDown().firstOrNull { it.name == "BuildConfig.java" }
        if (buildConfig == null) {
            problems += "Не найден сгенерированный release BuildConfig — проверка неполная."
        } else {
            val text = buildConfig.readText()
            fun stringField(name: String): String? =
                Regex("""String $name = "([^"]*)"""").find(text)?.groupValues?.get(1)
            fun boolField(name: String): Boolean? =
                Regex("""boolean $name = (true|false)""").find(text)?.groupValues?.get(1)?.toBooleanStrictOrNull()

            val applicationId = stringField("APPLICATION_ID").orEmpty()
            val versionName = stringField("VERSION_NAME").orEmpty()
            val adsEnabled = boolField("ADS_ENABLED") ?: false
            val devMenu = boolField("DEVELOPER_MENU") ?: false
            val debuggable = boolField("DEBUG") ?: false
            val banner = stringField("AD_UNIT_BANNER").orEmpty()
            val interstitial = stringField("AD_UNIT_INTERSTITIAL").orEmpty()
            val dbUrl = stringField("CAMERA_DB_BASE_URL").orEmpty()

            if (applicationId.startsWith("ru.example")) {
                problems += "applicationId всё ещё '$applicationId'. Выберите уникальный package name перед релизом."
            }
            if (devMenu) problems += "DEVELOPER_MENU=true в release-сборке."
            val appName = stringField("APP_NAME").orEmpty()
            if (appName.contains("Dev", ignoreCase = true)) {
                problems += "Имя приложения '$appName' содержит 'Dev'."
            }
            if (debuggable) problems += "BuildConfig.DEBUG=true в release-сборке."

            if (adsEnabled) {
                if (versionName.startsWith("1.0")) {
                    problems += "ADS_ENABLED=true при versionName '$versionName': в 1.0 реклама должна быть выключена до модерации РСЯ."
                }
                listOf("banner" to banner, "interstitial" to interstitial).forEach { (name, id) ->
                    if (id.isBlank()) {
                        problems += "ADS_ENABLED=true, но production ad unit '$name' не задан (ads.properties)."
                    } else if (id.startsWith("demo-")) {
                        problems += "Обнаружен demo ad unit '$id' в release-сборке."
                    }
                }
            }

            val mirrorUrl = stringField("CAMERA_DB_MIRROR_URL").orEmpty()
            if (mirrorUrl.isNotBlank() && !mirrorUrl.startsWith("https://")) {
                problems += "CAMERA_DB_MIRROR_URL должен быть HTTPS, сейчас '$mirrorUrl'."
            }
            if (mirrorUrl.contains("OWNER/REPO")) {
                problems += "CAMERA_DB_MIRROR_URL содержит placeholder OWNER/REPO."
            }

            if (!dbUrl.startsWith("https://")) {
                problems += "CAMERA_DB_BASE_URL должен быть HTTPS, сейчас '$dbUrl'."
            }
            if (dbUrl.contains("localhost") || dbUrl.contains("127.0.0.1") || dbUrl.contains("10.0.2.2")) {
                problems += "CAMERA_DB_BASE_URL указывает на localhost."
            }
            if (dbUrl.contains("OWNER/REPO")) {
                problems += "CAMERA_DB_BASE_URL содержит placeholder OWNER/REPO."
            }
        }

        // --- Merged manifest release ---
        val manifest = buildDirectory.resolve("intermediates")
            .walkTopDown()
            .firstOrNull { it.name == "AndroidManifest.xml" && it.path.contains("release", ignoreCase = true) }
        if (manifest == null) {
            problems += "Не найден merged AndroidManifest release — проверка разрешений неполная."
        } else {
            val manifestText = manifest.readText()
            forbiddenPermissions.forEach { permission ->
                if (manifestText.contains("\"$permission\"")) {
                    problems += "В release-манифесте присутствует запрещённое для 1.0 разрешение: $permission."
                }
            }
            if (manifestText.contains("android:debuggable=\"true\"")) {
                problems += "android:debuggable=\"true\" в release-манифесте."
            }
        }

        // --- Рекламному SDK нельзя отдавать геолокацию (ТЗ §27) ---
        val sources = rootDirectory.resolve("app/src/main")
        if (sources.exists()) {
            val offenders = sources.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    val text = file.readText()
                    text.contains("setLocationTracking(true)") || text.contains("setLocationConsent(true)")
                }
                .map { it.name }
                .toList()
            if (offenders.isNotEmpty()) {
                problems += "Передача геолокации в рекламный SDK включена в: " + offenders.joinToString()
            }
        }

        // --- Обязательные документы ---
        listOf(
            "legal/PRIVACY_POLICY_RU.md" to "Privacy Policy",
            "legal/OSM_ATTRIBUTION.md" to "OpenStreetMap attribution",
        ).forEach { (path, title) ->
            val f = rootDirectory.resolve(path)
            if (!f.exists() || f.length() < 200L) {
                problems += "$title отсутствует или пуст: $path."
            }
        }

        // --- Подпись ---
        val keystoreProperties = rootDirectory.resolve("keystore.properties")
        if (!keystoreProperties.exists()) {
            problems += "Не настроена release-подпись: нет keystore.properties (см. keystore.properties.example)."
        }

        // --- Секреты не должны попадать в Git ---
        val gitignore = rootDirectory.resolve(".gitignore")
        if (gitignore.exists()) {
            val ignored = gitignore.readText()
            listOf("*.jks", "*.keystore", "keystore.properties", "local.properties").forEach {
                if (!ignored.contains(it)) problems += ".gitignore не содержит '$it'."
            }
        }

        if (problems.isNotEmpty()) {
            val message = buildString {
                appendLine()
                appendLine("═══ RELEASE BUILD ЗАБЛОКИРОВАН (ТЗ §48) ═══")
                problems.forEach { appendLine("  ✗ $it") }
                appendLine()
                appendLine("Исправьте пункты выше и повторите сборку.")
            }
            throw GradleException(message)
        }
        logger.lifecycle("verifyReleaseConfig: все проверки пройдены.")
    }
}

// Проверка должна пройти ДО упаковки release-артефакта.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseConfig)
}
