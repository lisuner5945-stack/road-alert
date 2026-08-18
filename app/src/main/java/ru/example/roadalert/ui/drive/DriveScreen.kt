package ru.example.roadalert.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.example.roadalert.domain.model.DriveState
import ru.example.roadalert.domain.model.GpsStatus
import ru.example.roadalert.ui.components.CameraAheadBlock
import ru.example.roadalert.ui.components.SpeedDisplay
import ru.example.roadalert.ui.components.SpeedLimitSign
import ru.example.roadalert.ui.components.cameraTypeTitle
import ru.example.roadalert.ui.components.formatDistance
import ru.example.roadalert.ui.theme.AlertRed

/**
 * Экран активной поездки. Всё крупное, минимум текста, никакой рекламы.
 */
@Composable
fun DriveScreen(
    state: DriveState,
    onStopTrip: () -> Unit,
    onOpenHud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = gpsHint(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SpeedDisplay(
                        speedKmh = state.speedKmh,
                        isOverLimit = state.isOverSpeedLimit,
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LimitAndCamera(state)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SpeedDisplay(speedKmh = state.speedKmh, isOverLimit = state.isOverSpeedLimit)
                    LimitAndCamera(state)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenHud,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) {
                    Text("HUD", style = MaterialTheme.typography.titleLarge)
                }
                Button(
                    onClick = onStopTrip,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertRed,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("СТОП", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun LimitAndCamera(state: DriveState) {
    val limit = state.speedLimitKmh
    if (limit != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "ОГРАНИЧЕНИЕ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Box(Modifier.padding(top = 6.dp)) {
                SpeedLimitSign(limitKmh = limit)
            }
        }
    }

    val alert = state.alert
    if (alert != null) {
        CameraAheadBlock(
            typeTitle = cameraTypeTitle(alert.camera.type),
            distanceMeters = alert.distanceMeters,
            highlight = alert.distanceMeters < 350,
        )
    } else {
        Text(
            "Камер впереди не обнаружено",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }

    val section = state.averageSpeedSection
    if (section != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("СРЕДНЯЯ СКОРОСТЬ", style = MaterialTheme.typography.labelLarge)
            Text(
                "${section.averageSpeedKmh.toInt()} км/ч",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                buildString {
                    if (section.limitKmh != null) append("Лимит ${section.limitKmh} · ")
                    append("осталось ${formatDistance(section.remainingMeters)}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun gpsHint(state: DriveState): String = when (state.gpsStatus) {
    GpsStatus.READY -> "GPS: приём"
    GpsStatus.WAITING -> "Ожидание сигнала GPS…"
    GpsStatus.DISABLED -> "Геолокация выключена в системе"
    GpsStatus.NO_PERMISSION -> "Нет разрешения на геолокацию"
    GpsStatus.APPROXIMATE_ONLY -> "Выдана только приблизительная геолокация"
}
