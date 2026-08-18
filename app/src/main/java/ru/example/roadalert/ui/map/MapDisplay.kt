package ru.example.roadalert.ui.map

import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraType

/** Как отрисовывать камеры при текущем масштабе. */
enum class MapDisplayMode {
    /** Слишком мелкий масштаб: тысячи точек превратятся в кашу — не рисуем ничего. */
    HIDDEN,

    /** Средний масштаб: только точки, без знаков и цифр. */
    DOTS,

    /** Крупный масштаб: полноценные знаки с ограничением скорости. */
    SIGNS,
}

/**
 * Правила отображения камер на карте.
 *
 * Вынесено отдельно от отрисовки, потому что это единственная часть карты,
 * которую имеет смысл проверять юнит-тестами.
 */
object MapDisplay {

    /** Ниже этого масштаба камеры не показываем вообще. */
    const val MIN_ZOOM_VISIBLE = 8.0

    /** Начиная с этого масштаба рисуем знаки с цифрами. */
    const val MIN_ZOOM_SIGNS = 12.0

    /** Больше знаков на экране читать невозможно — переключаемся на точки. */
    const val MAX_SIGNS = 400

    /** Жёсткий предел на число точек: защита от просадки кадров. */
    const val MAX_DOTS = 4000

    fun modeFor(zoom: Double, camerasInView: Int): MapDisplayMode = when {
        camerasInView <= 0 -> MapDisplayMode.HIDDEN
        zoom < MIN_ZOOM_VISIBLE -> MapDisplayMode.HIDDEN
        zoom < MIN_ZOOM_SIGNS -> MapDisplayMode.DOTS
        camerasInView > MAX_SIGNS -> MapDisplayMode.DOTS
        else -> MapDisplayMode.SIGNS
    }

    /** Подсказка под шапкой карты; null — подсказка не нужна. */
    fun hintFor(zoom: Double, camerasInView: Int): String? {
        val mode = modeFor(zoom, camerasInView)
        return when {
            mode == MapDisplayMode.HIDDEN && zoom < MIN_ZOOM_VISIBLE ->
                "Приблизьте карту, чтобы увидеть камеры"

            mode == MapDisplayMode.HIDDEN -> "Здесь камер нет"

            mode == MapDisplayMode.DOTS && zoom < MIN_ZOOM_SIGNS ->
                "Приблизьте ещё, чтобы увидеть ограничения скорости"

            mode == MapDisplayMode.DOTS -> "Слишком много камер в кадре — показаны точками"

            else -> null
        }
    }

    /** Цифра внутри знака или null, если ограничение неизвестно. */
    fun signText(camera: CameraPoint): String? =
        camera.speedLimitKmh?.takeIf { it in 5..200 }?.toString()

    fun typeLabel(type: CameraType): String = when (type) {
        CameraType.SPEED_CAMERA -> "Контроль скорости"
        CameraType.RED_LIGHT -> "Контроль проезда на красный"
        CameraType.SPEED_AND_RED_LIGHT -> "Скорость и красный свет"
        CameraType.AVERAGE_SPEED_START -> "Начало участка средней скорости"
        CameraType.AVERAGE_SPEED_END -> "Конец участка средней скорости"
        CameraType.LANE_CONTROL -> "Контроль полосы"
        CameraType.UNKNOWN -> "Тип не указан"
    }

    /** Направление контроля словами: «на северо-восток». */
    fun directionLabel(degrees: Double?): String {
        if (degrees == null) return "направление не указано"
        val names = listOf(
            "север", "северо-восток", "восток", "юго-восток",
            "юг", "юго-запад", "запад", "северо-запад",
        )
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        val index = Math.round(normalized / 45.0).toInt() % names.size
        return "смотрит на " + names[index]
    }

    fun speedLabel(camera: CameraPoint): String =
        camera.speedLimitKmh?.let { "$it км/ч" } ?: "ограничение неизвестно"
}
