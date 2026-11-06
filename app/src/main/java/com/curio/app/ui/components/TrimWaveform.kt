package com.curio.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.curio.app.ui.theme.CurioColors
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons

/**
 * A waveform display with two draggable trim handles (start / end).
 *
 * [samples] is an array of normalized amplitudes (0.0–1.0) from [WaveformExtractor].
 * [startTrim] and [endTrim] are fractions (0.0–1.0) representing the selected range.
 * [onTrimChange] is called when the user drags either handle.
 *
 * The selected region (between handles) is rendered with full opacity in [accent].
 * The trimmed regions (outside) are dimmed with a scrim overlay.
 */
@Composable
fun TrimWaveform(
    samples: FloatArray,
    startTrim: Float,
    endTrim: Float,
    accent: Color,
    tint: Color,
    totalSeconds: Int,
    onTrimChange: (start: Float, end: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragTarget by remember { mutableStateOf<TrimHandle?>(null) }

    val handleAlpha by animateFloatAsState(
        targetValue = if (dragTarget != null) 1f else 0.8f,
        animationSpec = tween(200),
        label = "handleAlpha"
    )

    Column(modifier = modifier) {
        // Label showing selected range duration
        val selDurationMs = ((endTrim - startTrim) * totalSeconds * 1000).toLong().coerceAtLeast(0)
        val startLabel = formatTrimTime((startTrim * totalSeconds).toInt())
        val endLabel = formatTrimTime((endTrim * totalSeconds).toInt())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurioIcon(
                name = CurioIcons.Edit,
                contentDescription = null,
                tint = accent,
                size = 16.dp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$startLabel – $endLabel",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTrimDuration(selDurationMs),
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }

        // ── Waveform canvas ──────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.06f))
                .pointerInput(startTrim, endTrim) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        // Tap moves the nearest handle
                        val distToStart = kotlin.math.abs(fraction - startTrim)
                        val distToEnd = kotlin.math.abs(fraction - endTrim)
                        if (distToStart < distToEnd) {
                            onTrimChange(
                                (fraction - 0.05f).coerceIn(0f, endTrim - 0.02f),
                                endTrim
                            )
                        } else {
                            onTrimChange(
                                startTrim,
                                (fraction + 0.05f).coerceIn(startTrim + 0.02f, 1f)
                            )
                        }
                    }
                }
                .pointerInput(startTrim, endTrim) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val distToStart = kotlin.math.abs(fraction - startTrim)
                            val distToEnd = kotlin.math.abs(fraction - endTrim)
                            dragTarget = if (distToStart < distToEnd) TrimHandle.START else TrimHandle.END
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = dragAmount.x / size.width
                            when (dragTarget) {
                                TrimHandle.START -> {
                                    val newStart = (startTrim + delta).coerceIn(0f, endTrim - 0.02f)
                                    onTrimChange(newStart, endTrim)
                                }
                                TrimHandle.END -> {
                                    val newEnd = (endTrim + delta).coerceIn(startTrim + 0.02f, 1f)
                                    onTrimChange(startTrim, newEnd)
                                }
                                null -> {}
                            }
                        },
                        onDragEnd = { dragTarget = null },
                        onDragCancel = { dragTarget = null }
                    )
                }
        ) {
            if (samples.isEmpty()) return@Canvas

            val barCount = samples.size
            val gap = 2.dp.toPx()
            val totalGap = gap * (barCount - 1)
            val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(1f)

            // Draw each bar
            for (i in 0 until barCount) {
                val barHeight = samples[i] * size.height * 0.85f
                val x = i * (barWidth + gap)
                val y = (size.height - barHeight) / 2f
                val fraction = i.toFloat() / barCount
                val isSelected = fraction >= startTrim && fraction <= endTrim

                drawRoundRect(
                    color = if (isSelected) accent.copy(alpha = 0.9f)
                            else accent.copy(alpha = 0.2f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3f)
                )
            }

            // ── Start trim handle ──────────────────────────────────────
            val startX = startTrim * size.width
            drawLine(
                color = CurioColors.ButterYellow.copy(alpha = handleAlpha),
                start = Offset(startX, 0f),
                end = Offset(startX, size.height),
                strokeWidth = 3.dp.toPx()
            )
            // Handle circle at top
            drawCircle(
                color = CurioColors.ButterYellow,
                radius = 8.dp.toPx(),
                center = Offset(startX, 8.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(startX, 8.dp.toPx())
            )

            // ── End trim handle ────────────────────────────────────────
            val endX = endTrim * size.width
            drawLine(
                color = CurioColors.ButterYellow.copy(alpha = handleAlpha),
                start = Offset(endX, 0f),
                end = Offset(endX, size.height),
                strokeWidth = 3.dp.toPx()
            )
            drawCircle(
                color = CurioColors.ButterYellow,
                radius = 8.dp.toPx(),
                center = Offset(endX, 8.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(endX, 8.dp.toPx())
            )

            // ── Scrim outside the selection ────────────────────────────
            // Left scrim
            if (startTrim > 0.01f) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset.Zero,
                    size = Size(startX, size.height)
                )
            }
            // Right scrim
            if (endTrim < 0.99f) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(endX, 0f),
                    size = Size(size.width - endX, size.height)
                )
            }
        }
    }
}

private enum class TrimHandle { START, END }

/** Format seconds to mm:ss. */
private fun formatTrimTime(totalSecs: Int): String {
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "%d:%02d".format(m, s)
}

/** Format ms duration to a short label like "9.0s" or "1m 12s". */
private fun formatTrimDuration(ms: Long): String {
    val totalSecs = (ms / 1000).toInt()
    return when {
        totalSecs < 60 -> "${totalSecs}s"
        else -> "${totalSecs / 60}m ${totalSecs % 60}s"
    }
}
