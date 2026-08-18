package ru.example.roadalert.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.example.roadalert.domain.model.CameraType
import ru.example.roadalert.ui.theme.AlertRed
import ru.example.roadalert.ui.theme.SafeGreen
import ru.example.roadalert.ui.theme.SpeedWhite

/** Крупная текущая скорость — главный элемент экрана поездки. */
@Composable
fun SpeedDisplay(
    speedKmh: Double?,
    isOverLimit: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val color = when {
        speedKmh == null -> Color(0xFF7A8796)
        isOverLimit -> AlertRed
        else -> SpeedWhite
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = speedKmh?.let { formatSpeed(it) } ?: "--",
            color = color,
            style = if (compact) {
                MaterialTheme.typography.displayMedium
            } else {
                MaterialTheme.typography.displayLarge
            },
            textAlign = TextAlign.Center,
        )
        Text(
            text = "км/ч",
            color = color.copy(alpha = 0.75f),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** Блок «камера впереди»: тип, расстояние. */
@Composable
fun CameraAheadBlock(
    typeTitle: String,
    distanceMeters: Double,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "📷  " + typeTitle.uppercase(),
            color = if (highlight) AlertRed else SafeGreen,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formatDistance(distanceMeters),
            color = SpeedWhite,
            style = MaterialTheme.typography.displaySmall,
        )
    }
}

fun formatSpeed(speedKmh: Double): String = speedKmh.toInt().toString()

fun formatDistance(meters: Double): String = when {
    meters >= 1000 -> String.format(java.util.Locale.getDefault(), "%.1f км", meters / 1000.0)
    else -> "${(meters / 10).toInt() * 10} м"
}

fun cameraTypeTitle(type: CameraType): String = when (type) {
    CameraType.SPEED_CAMERA -> "Камера скорости"
    CameraType.RED_LIGHT -> "Камера на светофоре"
    CameraType.SPEED_AND_RED_LIGHT -> "Скорость и светофор"
    CameraType.AVERAGE_SPEED_START -> "Начало средней скорости"
    CameraType.AVERAGE_SPEED_END -> "Конец средней скорости"
    CameraType.LANE_CONTROL -> "Контроль полосы"
    CameraType.UNKNOWN -> "Камера контроля"
}
