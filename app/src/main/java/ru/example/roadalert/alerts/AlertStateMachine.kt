package ru.example.roadalert.alerts

import ru.example.roadalert.data.settings.DistanceProfile
import ru.example.roadalert.detection.DetectionConfig
import ru.example.roadalert.domain.model.AlertStage
import ru.example.roadalert.domain.model.DetectedCamera

/** Событие, требующее озвучки/сигнала. */
data class AlertEvent(
    val detected: DetectedCamera,
    val stage: AlertStage,
)

/**
 * Не даёт одной камере озвучиваться каждую секунду (ТЗ §18).
 *
 * Для каждой камеры хранится стадия: как только стадия пройдена, повторно она
 * не срабатывает. После проезда камера уходит в COOLDOWN и вернётся к жизни
 * только если водитель реально удалился или прошло много времени (разворот).
 */
class AlertStateMachine(
    private val distanceProfileProvider: () -> DistanceProfile = { DistanceProfile.AUTO },
) {

    private data class CameraState(
        var stage: AlertStage,
        var minDistanceMeters: Double,
        var lastSeenAtMs: Long,
        var passedAtMs: Long? = null,
    )

    private val states = HashMap<String, CameraState>()

    /**
     * @param candidates камеры, признанные релевантными на этом GPS-fix
     * @return события, которые нужно озвучить (обычно 0 или 1)
     */
    fun onUpdate(
        candidates: List<DetectedCamera>,
        speedKmh: Double,
        nowMs: Long,
    ): List<AlertEvent> {
        val distances = DetectionConfig.alertDistances(speedKmh, distanceProfileProvider())
        val events = mutableListOf<AlertEvent>()

        candidates.forEach { detected ->
            val id = detected.camera.id
            val state = states.getOrPut(id) {
                CameraState(AlertStage.NOT_SEEN, detected.distanceMeters, nowMs)
            }
            state.lastSeenAtMs = nowMs

            // Камера снова приближается после проезда — например, водитель развернулся.
            if (state.stage == AlertStage.PASSED || state.stage == AlertStage.COOLDOWN) {
                val cooledDown = state.passedAtMs?.let { nowMs - it >= DetectionConfig.COOLDOWN_MS } ?: true
                val wentFarAway =
                    detected.distanceMeters > DetectionConfig.COOLDOWN_RESET_DISTANCE_METERS
                if (cooledDown && wentFarAway) {
                    state.stage = AlertStage.NOT_SEEN
                    state.minDistanceMeters = detected.distanceMeters
                    state.passedAtMs = null
                } else {
                    state.stage = AlertStage.COOLDOWN
                    return@forEach
                }
            }

            // Удаляемся от камеры — считаем её пройденной.
            val movingAway = detected.distanceMeters >
                state.minDistanceMeters + DetectionConfig.PASSED_DISTANCE_METERS
            if (movingAway && state.stage != AlertStage.NOT_SEEN) {
                state.stage = AlertStage.PASSED
                state.passedAtMs = nowMs
                return@forEach
            }

            if (detected.distanceMeters < state.minDistanceMeters) {
                state.minDistanceMeters = detected.distanceMeters
            }

            val targetStage = when {
                detected.distanceMeters <= distances.final -> AlertStage.FINAL_ALERTED
                detected.distanceMeters <= distances.main -> AlertStage.MAIN_ALERTED
                detected.distanceMeters <= distances.pre -> AlertStage.PRE_ALERTED
                else -> AlertStage.NOT_SEEN
            }

            if (targetStage.ordinal > state.stage.ordinal && targetStage != AlertStage.NOT_SEEN) {
                state.stage = targetStage
                events += AlertEvent(detected, targetStage)
            }
        }

        forgetStaleCameras(nowMs)
        return events
    }

    /** Текущая стадия камеры (для UI и тестов). */
    fun stageOf(cameraId: String): AlertStage = states[cameraId]?.stage ?: AlertStage.NOT_SEEN

    fun reset() = states.clear()

    private fun forgetStaleCameras(nowMs: Long) {
        if (states.size < MAX_TRACKED_CAMERAS) return
        val iterator = states.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value.lastSeenAtMs > STALE_AFTER_MS) iterator.remove()
        }
    }

    private companion object {
        const val MAX_TRACKED_CAMERAS = 64
        const val STALE_AFTER_MS = 10 * 60_000L
    }
}
