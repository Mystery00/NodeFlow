package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun ZoomableImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var scale by remember(imageUrl) { mutableStateOf(1f) }
        var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, MaxImageScale)
            scale = nextScale
            offset = if (nextScale > 1f) {
                clampImageOffset(offset + panChange, nextScale)
            } else {
                Offset.Zero
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .pointerInput(imageUrl) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = DoubleTapImageScale
                                }
                            },
                        )
                    }
                    .transformable(transformableState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WindowInsets.systemBars.asPaddingValues())
                    .padding(12.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭大图",
                    )
                }
            }
        }
    }
}

private fun clampImageOffset(offset: Offset, scale: Float): Offset {
    val maxOffset = MaxImageTranslation * (scale - 1f)
    return Offset(
        x = offset.x.coerceIn(-maxOffset, maxOffset),
        y = offset.y.coerceIn(-maxOffset, maxOffset),
    )
}

private const val MaxImageScale = 5f
private const val DoubleTapImageScale = 2.5f
private const val MaxImageTranslation = 1200f
