package ru.example.roadalert.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.example.roadalert.domain.model.DriveState
import ru.example.roadalert.ui.components.SpeedDisplay
import ru.example.roadalert.ui.components.SpeedLimitSign
import ru.example.roadalert.ui.components.cameraTypeTitle
import ru.example.roadalert.ui.components.formatDistance

/**
 * HUD: чёрный фон и зеркальное отражение для проекции на лобовое стекло.
 * Реклама здесь запрещена (ТЗ §22).
 */
@Composable
fun HudScreen(
    state: DriveState,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onExit),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpeedDisplay(speedKmh = state.speedKmh, isOverLimit = state.isOverSpeedLimit)

            state.speedLimitKmh?.let { SpeedLimitSign(limitKmh = it, diameter = 72) }

            state.alert?.let { alert ->
                Text(
                    "📷  " + cameraTypeTitle(alert.camera.type),
                    color = Color(0xFF66FF99),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    formatDistance(alert.distanceMeters),
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }
}
