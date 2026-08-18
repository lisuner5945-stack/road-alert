package ru.example.roadalert.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import ru.example.roadalert.domain.model.AlertStage
import ru.example.roadalert.domain.model.CameraType
import ru.example.roadalert.domain.model.DetectedCamera
import ru.example.roadalert.util.AppLog
import java.util.Locale

/**
 * Озвучка предупреждений через системный TextToSpeech (ТЗ §19, §20).
 *
 * Требования: русская локаль, graceful fallback, очередь без наложений,
 * debounce и корректное освобождение ресурсов.
 */
class VoiceAlertManager(context: Context) {

    data class Options(
        val voiceEnabled: Boolean = true,
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = false,
    )

    private val appContext = context.applicationContext

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var russianAvailable = false
    private var lastSpokenAtMs = 0L
    private var lastPhrase: String? = null

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    }.getOrNull()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getSystemService(appContext, VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(appContext, Vibrator::class.java)
        }
    }

    fun initialize(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(ttsReady)
            return
        }
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) configureEngine() else AppLog.event("TTS_INIT_FAILED")
            onReady(ttsReady)
        }
    }

    private fun configureEngine() {
        val engine = tts ?: return
        val result = runCatching { engine.setLanguage(Locale("ru", "RU")) }.getOrNull()
        russianAvailable = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!russianAvailable) {
            // Русского голоса нет: остаёмся на языке системы, звуковой сигнал продолжает работать.
            AppLog.event("TTS_RU_MISSING")
            runCatching { engine.setLanguage(Locale.getDefault()) }
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = Unit

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                AppLog.event("TTS_ERROR", "id" to utteranceId)
            }
        })
    }

    val isRussianVoiceAvailable: Boolean get() = russianAvailable

    /** Озвучивает событие предупреждения с учётом настроек пользователя. */
    fun announce(
        event: AlertEvent,
        currentSpeedKmh: Double,
        options: Options,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (options.soundEnabled) beep(event.stage)
        if (options.vibrationEnabled) vibrate()
        if (options.voiceEnabled) {
            speak(
                text = buildPhrase(event, currentSpeedKmh),
                important = event.stage != AlertStage.PRE_ALERTED,
                nowMs = nowMs,
            )
        }
    }

    fun speak(text: String, important: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val engine = tts ?: return
        if (!ttsReady) return

        // Debounce: не повторяем ту же фразу и не тараторим (ТЗ §20).
        if (text == lastPhrase && nowMs - lastSpokenAtMs < REPEAT_DEBOUNCE_MS) return
        if (!important && nowMs - lastSpokenAtMs < MIN_GAP_MS) return

        // Важное предупреждение вытесняет устаревшее, обычное встаёт в очередь.
        val queueMode = if (important) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(text, queueMode, null, UTTERANCE_ID)
        if (result == TextToSpeech.SUCCESS) {
            lastSpokenAtMs = nowMs
            lastPhrase = text
            AppLog.event("TTS_SPEAK", "important" to important)
        }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        runCatching { toneGenerator?.release() }
        AppLog.event("TTS_SHUTDOWN")
    }

    private fun beep(stage: AlertStage) {
        val tone = if (stage == AlertStage.FINAL_ALERTED) {
            ToneGenerator.TONE_PROP_BEEP2
        } else {
            ToneGenerator.TONE_PROP_BEEP
        }
        runCatching { toneGenerator?.startTone(tone, TONE_DURATION_MS) }
    }

    private fun vibrate() {
        val device = vibrator ?: return
        runCatching {
            device.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {

        private const val UTTERANCE_ID = "road_alert"
        private const val TONE_VOLUME = 80
        private const val TONE_DURATION_MS = 180
        private const val VIBRATION_MS = 220L
        private const val REPEAT_DEBOUNCE_MS = 20_000L
        private const val MIN_GAP_MS = 4_000L

        /**
         * Формирование русской фразы — чистая функция, покрывается unit-тестами.
         * Фраз намеренно мало: водителя нельзя перегружать (ТЗ §19).
         */
        fun buildPhrase(event: AlertEvent, currentSpeedKmh: Double): String {
            val detected: DetectedCamera = event.detected
            val limit = detected.camera.speedLimitKmh
            val typeName = when (detected.camera.type) {
                CameraType.RED_LIGHT -> "камера на светофоре"
                CameraType.SPEED_AND_RED_LIGHT -> "камера скорости и светофора"
                CameraType.AVERAGE_SPEED_START -> "начало участка средней скорости"
                CameraType.AVERAGE_SPEED_END -> "конец участка средней скорости"
                CameraType.LANE_CONTROL -> "контроль полосы"
                else -> "камера контроля скорости"
            }
            val overLimit = limit != null && currentSpeedKmh > limit

            return when (event.stage) {
                AlertStage.PRE_ALERTED -> buildString {
                    append("Впереди ").append(typeName).append(".")
                    if (limit != null) append(" Ограничение ").append(limit).append(".")
                }

                AlertStage.MAIN_ALERTED -> if (overLimit) {
                    "Снизьте скорость. Ограничение $limit."
                } else {
                    val distance = roundDistance(detected.distanceMeters)
                    typeName.replaceFirstChar { it.uppercase() } + " через $distance метров."
                }

                AlertStage.FINAL_ALERTED -> if (overLimit) {
                    "Внимание, камера. Ограничение $limit."
                } else {
                    "Внимание, камера."
                }

                else -> typeName.replaceFirstChar { it.uppercase() }
            }
        }

        private fun roundDistance(meters: Double): Int {
            val step = if (meters >= 1000) 100 else 50
            return (Math.round(meters / step) * step).toInt().coerceAtLeast(step)
        }
    }
}
