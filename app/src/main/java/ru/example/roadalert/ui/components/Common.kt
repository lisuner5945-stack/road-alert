package ru.example.roadalert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Строка статуса вида «GPS: готов» с цветным индикатором. */
@Composable
fun StatusRow(
    label: String,
    value: String,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(indicatorColor, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
    }
}

/** Крупный дорожный знак ограничения скорости. */
@Composable
fun SpeedLimitSign(
    limitKmh: Int,
    modifier: Modifier = Modifier,
    diameter: Int = 96,
) {
    Box(
        modifier = modifier
            .size(diameter.dp)
            .background(Color.White, CircleShape)
            .border(diameter.dp / 12, Color(0xFFD32F2F), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = limitKmh.toString(),
            color = Color.Black,
            style = MaterialTheme.typography.displaySmall,
        )
    }
}

/** Карточка с заголовком, используется на Home/Settings/About. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}
