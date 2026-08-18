package ru.example.roadalert.detection

import ru.example.roadalert.data.settings.DistanceProfile

/**
 * Все пороги детекции собраны здесь: никаких magic numbers по проекту (ТЗ §16).
 */
object DetectionConfig {

    /** Радиус поиска камер в зависимости от скорости, метры (ТЗ §16). */
    fun searchRadiusMeters(speedKmh: Double): Double = when {
        speedKmh < 30 -> 700.0
        speedKmh < 60 -> 1000.0
        speedKmh < 90 -> 1500.0
        speedKmh < 130 -> 2000.0
        else -> 2500.0
    }

    /** Порог «камера точно впереди». */
    const val ANGLE_AHEAD_DEGREES = 45.0

    /** Порог «возможно впереди, уверенность снижена». */
    const val ANGLE_UNCERTAIN_DEGREES = 70.0

    /**
     * Допустимое расхождение курса автомобиля и направления камеры.
     * Камера «смотрит» на встречный поток, если расхождение около 180°.
     */
    const val CAMERA_DIRECTION_TOLERANCE_DEGREES = 60.0

    /** Ближе этого расстояния угол на камеру шумит, поэтому не отбрасываем по углу. */
    const val ANGLE_CHECK_MIN_DISTANCE_METERS = 60.0

    /** Минимальное смещение для расчёта курса по трассе точек. */
    const val BEARING_MIN_DISPLACEMENT_METERS = 12.0

    /** Ниже этой скорости курс GPS недостоверен. */
    const val BEARING_MIN_SPEED_KMH = 8.0

    /** Хуже этой точности fix считается плохим, если есть более качественные. */
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 60f

    /** Камера считается пройденной, когда удалилась дальше этого расстояния. */
    const val PASSED_DISTANCE_METERS = 120.0

    /** Повторно предупреждать о той же камере не раньше, чем через столько мс. */
    const val COOLDOWN_MS = 90_000L

    /** Удалились дальше — камеру можно снова считать «новой» (например, после разворота). */
    const val COOLDOWN_RESET_DISTANCE_METERS = 1_200.0

    /**
     * Дистанции стадий предупреждения. Зависят от скорости: на 130 км/ч
     * 500 метров — это меньше 15 секунд, предупреждать нужно раньше.
     */
    fun alertDistances(speedKmh: Double, profile: DistanceProfile): AlertDistances {
        val base = when {
            speedKmh < 40 -> AlertDistances(pre = 600.0, main = 300.0, final = 120.0)
            speedKmh < 70 -> AlertDistances(pre = 800.0, main = 400.0, final = 150.0)
            speedKmh < 100 -> AlertDistances(pre = 1000.0, main = 500.0, final = 200.0)
            speedKmh < 130 -> AlertDistances(pre = 1400.0, main = 700.0, final = 250.0)
            else -> AlertDistances(pre = 1800.0, main = 900.0, final = 300.0)
        }
        val factor = when (profile) {
            DistanceProfile.EARLY -> 1.35
            DistanceProfile.AUTO -> 1.0
            DistanceProfile.LATE -> 0.75
        }
        return AlertDistances(base.pre * factor, base.main * factor, base.final * factor)
    }
}

data class AlertDistances(
    val pre: Double,
    val main: Double,
    val final: Double,
)
