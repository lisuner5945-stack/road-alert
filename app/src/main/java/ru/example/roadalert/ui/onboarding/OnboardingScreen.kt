package ru.example.roadalert.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.example.roadalert.ui.components.SectionCard

/**
 * Короткий онбординг: что делает приложение, чего не делает и зачем геолокация.
 * Никаких аккаунтов и длинных шагов (ТЗ §41).
 */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ROAD ALERT", style = MaterialTheme.typography.displaySmall)
        Text(
            "GPS-предупреждения о камерах и ограничениях скорости.",
            style = MaterialTheme.typography.bodyLarge,
        )

        SectionCard("Как это работает") {
            Text(
                "Приложение сравнивает ваши GPS-координаты с локальной базой камер " +
                    "и заранее предупреждает голосом о камере впереди по ходу движения.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Зачем нужна точная геолокация") {
            Text(
                "Без точных координат невозможно определить расстояние до камеры и " +
                    "направление движения. Обработка выполняется локально на телефоне, " +
                    "маршрут никуда не передаётся.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Чего приложение не делает") {
            Text(
                "• не принимает радиосигналы радаров\n" +
                    "• не глушит оборудование\n" +
                    "• не гарантирует наличие всех камер в базе\n" +
                    "• не сохраняет историю поездок на сервере",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Безопасность") {
            Text(
                "Приложение является вспомогательным средством. Соблюдайте ПДД и " +
                    "дорожные знаки. Не взаимодействуйте с телефоном во время движения.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Понятно, продолжить", style = MaterialTheme.typography.titleLarge)
        }
    }
}
