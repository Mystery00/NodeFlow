package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp

/** 图片加载中的占位：柔和底色 + 居中转圈动画。 */
@Composable
fun ContentImageLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 图片加载失败的占位：自绘的“破损图片”插图 + 提示，整块可点击重试。 */
@Composable
fun ContentImageErrorPlaceholder(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onRetry)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        BrokenImageArt(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp, 44.dp),
        )
        Text(
            text = "图片加载失败，点击重试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrokenImageArt(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val soft = tint.copy(alpha = 0.55f)
        val stroke = Stroke(
            width = (w * 0.05f).coerceAtLeast(2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        // 相框
        drawRoundRect(
            color = soft,
            topLeft = Offset(w * 0.06f, h * 0.10f),
            size = Size(w * 0.88f, h * 0.80f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = stroke,
        )
        // 太阳
        drawCircle(
            color = soft,
            radius = w * 0.07f,
            center = Offset(w * 0.30f, h * 0.34f),
        )
        // 远近两座山
        drawPath(
            path = Path().apply {
                moveTo(w * 0.12f, h * 0.80f)
                lineTo(w * 0.38f, h * 0.48f)
                lineTo(w * 0.52f, h * 0.66f)
            },
            color = soft,
            style = stroke,
        )
        drawPath(
            path = Path().apply {
                moveTo(w * 0.46f, h * 0.76f)
                lineTo(w * 0.66f, h * 0.46f)
                lineTo(w * 0.90f, h * 0.74f)
            },
            color = soft,
            style = stroke,
        )
        // 裂痕（闪电状），用实色强调“破损”
        drawPath(
            path = Path().apply {
                moveTo(w * 0.64f, h * 0.12f)
                lineTo(w * 0.57f, h * 0.40f)
                lineTo(w * 0.67f, h * 0.44f)
                lineTo(w * 0.59f, h * 0.78f)
            },
            color = tint,
            style = stroke,
        )
    }
}
