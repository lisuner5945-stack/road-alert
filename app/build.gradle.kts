import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Локальные (не коммитятся) конфиги. Отсутствие файла — нормальная ситуация:
 * debug-сборка работает на официальных demo-блоках Яндекса и debug-подписи.
 */
fun loadLocalProps(name: String): Properties {
    val props = Properties()
    val file = rootProject.file(name)
    if (file.exists()) file.inputStream().use { props.load(it) }
    return props
}

val keystoreProps = loadLocalProps("keystore.properties")
val adsProps = loadLocalProps("ads.properties")

val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.isNotBlank() == true &&
    file(keystoreProps.getProperty("storeFile", "")).exists()

android {
    namespace = "ru.example.roadalert"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.example.roadalert"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        // Источники базы камер: основной и запасной. Домены разные намеренно —
        // если провайдер не пускает к одному, приложение возьмёт базу со второго.
        // Проверяется release-задачей: только HTTPS, без localhost.
        buildConfigField(
            "String",
            "CAMERA_DB_BASE_URL",
            "\"https://raw.githubusercontent.com/lisuner5945-stack/road-alert/main/database/\"",
        )
        buildConfigField(
            "String",
            "CAMERA_DB_MIRROR_URL",
            "\"https://lisuner5945-stack.github.io/road-alert/database/\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            resValue("string", "app_name", "Road Alert Dev")
            buildConfigField("String", "APP_NAME", "\"Road Alert Dev\"")
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("boolean", "DEVELOPER_MENU", "true")
            buildConfigField("String", "AD_UNIT_BANNER", "\"demo-banner-yandex\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"demo-interstitial-yandex\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "Road Alert")
            buildConfigField("String", "APP_NAME", "\"Road Alert\"")
            // В 1.0 реклама выключена до прохождения модерации РСЯ (см. ТЗ §48).
            buildConfigField("boolean", "ADS_ENABLED", adsProps.getProperty("adsEnabled", "false"))
            buildConfigField("boolean", "DEVELOPER_MENU", "false")
            buildConfigField("String", "AD_UNIT_BANNER", "\"${adsProps.getProperty("bannerUnitId", "")}\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"${adsProps.getProperty("interstitialUnitId", "")}\"")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    androidResources {
        localeFilters += listOf("ru", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            // Версии закреплены осознанно: более новые требуют compileSdk 37.
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "ObsoleteLintCustomCheck",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.yandex.mobileads)

    // Карта: OpenStreetMap без Google Play Services, без ключей API и без платы
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
}

apply(from = "$rootDir/gradle/release-checks.gradle.kts")
