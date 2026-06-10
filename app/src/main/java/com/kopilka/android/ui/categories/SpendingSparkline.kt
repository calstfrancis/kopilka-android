package com.kopilka.android.ui.categories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SpendingSparkline(
    dailySpend: List<Float>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    if (dailySpend.isEmpty() || dailySpend.all { it == 0f }) return

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxVal = dailySpend.max()

    Canvas(modifier = modifier.fillMaxWidth().height(28.dp)) {
        val barCount = dailySpend.size
        val gap = 3.dp.toPx()
        val totalGap = gap * (barCount - 1)
        val barWidth = (size.width - totalGap) / barCount
        val maxBarHeight = size.height

        dailySpend.forEachIndexed { i, value ->
            val x = i * (barWidth + gap)
            // Track (empty bar)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, maxBarHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            // Filled portion
            if (value > 0f) {
                val filledHeight = (value / maxVal) * maxBarHeight
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, maxBarHeight - filledHeight),
                    size = Size(barWidth, filledHeight),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}
