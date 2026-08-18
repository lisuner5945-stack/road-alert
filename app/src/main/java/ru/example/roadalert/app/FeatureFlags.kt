package ru.example.roadalert.app

/**
 * Функции, сознательно выключенные в версии 1.0.
 */
object FeatureFlags {

    /**
     * Пользовательские сообщения о камерах (ТЗ §31).
     *
     * Выключено. Полноценный crowdsourcing требует backend, антиспам,
     * модерацию, репутацию, изменения Privacy Policy и правил
     * пользовательского контента — это Phase 2, а не 1.0.
     *
     * Токены доступа к внешним сервисам внутрь APK не кладутся ни при каких
     * условиях: сообщения будут уходить через собственный backend, когда он появится.
     */
    const val USER_CAMERA_REPORTS_ENABLED = false

    /**
     * Полная поддержка участков средней скорости (ТЗ §24).
     *
     * Модель данных и UI уже готовы (AverageSpeedSectionState, DriveScreen),
     * pipeline размечает `enforcement=average_speed`. Включать после того,
     * как обычные камеры стабильно работают на реальных поездках.
     */
    const val AVERAGE_SPEED_SECTIONS_ENABLED = false
}
