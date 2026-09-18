package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class NodeChipLayout(
    val height: Dp,
    val horizontalPadding: Dp,
)

fun compactNodeChipLayout(): NodeChipLayout = NodeChipLayout(
    height = 24.dp,
    horizontalPadding = 8.dp,
)

@Composable
fun NodeChip(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactLabelChip(
        title = title,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun StatusChip(
    title: String,
    modifier: Modifier = Modifier,
) {
    CompactLabelChip(
        title = title,
        onClick = null,
        modifier = modifier,
    )
}

@Composable
private fun CompactLabelChip(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val layout = compactNodeChipLayout()
    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .height(layout.height)
                .padding(horizontal = layout.horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val chipModifier = modifier.height(layout.height)
    val chipShape = RoundedCornerShape(6.dp)
    if (onClick == null) {
        Surface(
            modifier = chipModifier,
            shape = chipShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = chipModifier,
            shape = chipShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            content = content,
        )
    }
}
