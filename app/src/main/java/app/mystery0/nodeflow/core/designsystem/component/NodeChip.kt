package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    val layout = compactNodeChipLayout()
    Surface(
        onClick = onClick,
        modifier = modifier.height(layout.height),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
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
}
