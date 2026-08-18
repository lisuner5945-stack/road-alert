package ru.example.roadalert.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.example.roadalert.ui.components.SectionCard

/**
 * «О данных / Источники» — обязательный экран с ODbL-атрибуцией OpenStreetMap (ТЗ §9).
 */
@Composable
fun AboutScreen(
    appVersion: String,
    databaseVersion: String,
    cameraCount: Int,
    onOpenPrivacyPolicy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("О данных", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Источник данных о камерах") {
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Данные OpenStreetMap распространяются по лицензии ODbL " +
                    "(Open Database License).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "База камер построена на основе открытых данных OpenStreetMap и не " +
                    "является полной. Приложение не гарантирует наличие всех камер.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Подложка на экране «Карта камер» — тоже OpenStreetMap: изображения " +
                    "карты загружаются с публичных серверов проекта.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Что это за приложение") {
            Text(
                "Road Alert — вспомогательное приложение, которое по GPS сравнивает ваше " +
                    "положение с локальной базой камер и заранее предупреждает о камере " +
                    "впереди по ходу движения.\n\n" +
                    "Это не радиоэлектронный радар-детектор: приложение не принимает " +
                    "радарные сигналы, не глушит оборудование и не вмешивается в работу камер.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Конфиденциальность") {
            Text(
                "Определение камер выполняется локально на устройстве. Приложение не " +
                    "ведёт историю поездок на сервере и не передаёт точные координаты " +
                    "рекламному SDK.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                Text("Политика конфиденциальности")
            }
        }

        SectionCard("Версии") {
            Text("Версия приложения: $appVersion", style = MaterialTheme.typography.bodyMedium)
            Text("Версия базы камер: $databaseVersion", style = MaterialTheme.typography.bodyMedium)
            Text("Камер в базе: $cameraCount", style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "Приложение является вспомогательным средством. Соблюдайте ПДД и дорожные знаки. " +
                "Не взаимодействуйте с телефоном во время движения.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}
