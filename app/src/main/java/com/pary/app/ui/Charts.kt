package com.pary.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawLine
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ParyBarEntry(
    val label: String,
    val value: Float,
    val valueLabel: String,
    val detail: String = "",
)

/**
 * Компактная столбчатая диаграмма без внешних chart-зависимостей.
 * Значения и подписи остаются обычным Compose-текстом, а столбцы и сетка рисуются Canvas.
 */
@Composable
fun ParyBarChart(
    entries: List<ParyBarEntry>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 124.dp,
) {
    if (entries.isEmpty()) {
        Text(
            "Пока недостаточно данных для графика.",
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val maxValue = entries.maxOfOrNull { it.value.coerceAtLeast(0f) }?.coerceAtLeast(1f) ?: 1f
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = entries.joinToString(separator = ". ") {
        "${it.label}: ${it.valueLabel}" + if (it.detail.isBlank()) "" else ", ${it.detail}"
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = "Столбчатый график. $description" },
        ) {
            val top = 6.dp.toPx()
            val bottom = 3.dp.toPx()
            val plotHeight = (size.height - top - bottom).coerceAtLeast(1f)
            val gridStroke = 1.dp.toPx()

            for (tick in 0..3) {
                val y = top + plotHeight * tick / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = gridStroke,
                    cap = StrokeCap.Round,
                )
            }

            val gap = 9.dp.toPx()
            val barWidth = ((size.width - gap * (entries.size + 1)) / entries.size).coerceAtLeast(3.dp.toPx())
            entries.forEachIndexed { index, entry ->
                val fraction = (entry.value.coerceAtLeast(0f) / maxValue).coerceIn(0f, 1f)
                val barHeight = plotHeight * fraction
                if (barHeight > 0.5f) {
                    val x = gap + index * (barWidth + gap)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, top + plotHeight - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            entries.forEach { entry ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        entry.valueLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                    )
                    if (entry.detail.isNotBlank()) {
                        Text(
                            entry.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
