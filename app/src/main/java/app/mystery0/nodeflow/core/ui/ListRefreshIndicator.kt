package app.mystery0.nodeflow.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun shouldShowListRefreshIndicator(
    isRefreshing: Boolean,
    itemCount: Int,
): Boolean = isRefreshing && itemCount > 0

internal enum class NodeFlowProgressIndicatorStyle {
    MaterialExpressive,
}

internal fun listRefreshIndicatorStyle(): NodeFlowProgressIndicatorStyle =
    NodeFlowProgressIndicatorStyle.MaterialExpressive

internal fun horizontalRefreshIndicatorStyle(): NodeFlowProgressIndicatorStyle =
    NodeFlowProgressIndicatorStyle.MaterialExpressive

@Composable
fun NodeFlowHorizontalRefreshIndicator(
    modifier: Modifier = Modifier,
) {
    when (horizontalRefreshIndicatorStyle()) {
        NodeFlowProgressIndicatorStyle.MaterialExpressive -> {
            NodeFlowExpressiveWavyIndicator(
                modifier = modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.ListRefreshIndicator(
    isRefreshing: Boolean,
    itemCount: Int,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val visible = shouldShowListRefreshIndicator(isRefreshing = isRefreshing, itemCount = itemCount)
    val state = rememberPullToRefreshState()

    LaunchedEffect(visible) {
        if (visible) {
            state.animateToThreshold()
        } else {
            state.animateToHidden()
        }
    }

    when (listRefreshIndicatorStyle()) {
        NodeFlowProgressIndicatorStyle.MaterialExpressive -> {
            PullToRefreshDefaults.IndicatorBox(
                modifier = modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding + 8.dp),
                state = state,
                isRefreshing = visible,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                elevation = PullToRefreshDefaults.LoadingIndicatorElevation,
            ) {
                NodeFlowExpressiveLoadingDots(
                    active = visible,
                    pullFraction = state.distanceFraction.coerceIn(0f, 1f),
                )
            }
        }
    }
}

@Composable
private fun NodeFlowExpressiveWavyIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "nodeflow-wavy-progress")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nodeflow-wavy-progress-phase",
    )
    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val centerY = size.height / 2f
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        val segmentWidth = size.width * 0.62f
        val segmentStart = -segmentWidth + (size.width + segmentWidth) * phase.value
        val segmentEnd = segmentStart + segmentWidth
        val path = Path()
        val amplitude = min(3.dp.toPx(), size.height / 2f)
        val wavelength = 48.dp.toPx()
        val step = max(3f, strokeWidth)
        var moved = false
        var x = max(0f, segmentStart)
        val endX = min(size.width, segmentEnd)
        while (x <= endX) {
            val segmentProgress = ((x - segmentStart) / segmentWidth).coerceIn(0f, 1f)
            val envelope = sin(segmentProgress * PI).toFloat()
            val y = centerY + sin((x / wavelength + phase.value * 2f) * PI * 2f).toFloat() *
                amplitude * envelope
            if (moved) {
                path.lineTo(x, y)
            } else {
                path.moveTo(x, y)
                moved = true
            }
            x += step
        }
        if (moved) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun NodeFlowExpressiveLoadingDots(
    active: Boolean,
    pullFraction: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "nodeflow-refresh-dots")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nodeflow-refresh-dots-phase",
    )
    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(width = 36.dp, height = 24.dp)) {
        val radius = 3.5.dp.toPx()
        val spacing = 10.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        repeat(3) { index ->
            val dotPhase = (phase.value + index / 3f) % 1f
            val pulse = if (active) {
                (sin(dotPhase * PI * 2f).toFloat() + 1f) / 2f
            } else {
                pullFraction
            }
            val y = centerY - if (active) (pulse - 0.5f) * 8.dp.toPx() else 0f
            drawCircle(
                color = color.copy(alpha = 0.46f + 0.54f * pulse),
                radius = radius * (0.78f + 0.3f * pulse),
                center = androidx.compose.ui.geometry.Offset(
                    x = centerX + (index - 1) * spacing,
                    y = y,
                ),
            )
        }
    }
}
