package ru.example.roadalert.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.example.roadalert.domain.model.DriveState

/**
 * Единственный источник правды о текущей поездке.
 *
 * Пишет сюда только foreground service (и debug-симулятор), читают UI, HUD,
 * overlay и AdsManager. Отдельный объект нужен потому, что состояние переживает
 * пересоздание Activity: сервис продолжает работать при выключенном экране.
 */
object DriveStateHolder {

    private val _state = MutableStateFlow(DriveState())
    val state: StateFlow<DriveState> = _state.asStateFlow()

    /** Быстрая синхронная проверка для защиты от полноэкранной рекламы (ТЗ §28). */
    val isTripActive: Boolean get() = _state.value.isTripActive

    fun update(transform: (DriveState) -> DriveState) = _state.update(transform)

    fun reset() {
        _state.value = DriveState()
    }
}
