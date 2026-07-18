package app.mystery0.nodeflow.feature.replyeditor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyEditorBottomSheet(
    state: ReplyEditorUiState,
    onEvent: (ReplyEditorUiEvent) -> Unit,
    onPickImage: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val maximumSheetHeight = if (WindowInsets.isImeVisible) maxHeight else maxHeight * 0.5f
        AnimatedVisibility(
            visible = state.isOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            TransparentNavigationBarWhileComposed()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maximumSheetHeight),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("创建回复", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${state.value.text.length}/${state.maxLength}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.value.text.length > state.maxLength) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        IconButton(onClick = { onEvent(ReplyEditorUiEvent.Close) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭回复编辑器")
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.value,
                            onValueChange = { onEvent(ReplyEditorUiEvent.ContentChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isUploading && !state.isSubmitting,
                            minLines = 5,
                            maxLines = 12,
                            placeholder = { Text("输入纯文字回复") },
                        )
                        if (state.images.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(72.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.images, key = { it.imageId }) { image ->
                                    AsyncImage(
                                        model = image.originalUrl,
                                        contentDescription = "已上传图片",
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                        state.message?.let { message ->
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = !state.isUploading && !state.isSubmitting,
                            onClick = { if (state.isLoggedIn) onPickImage() else onLoginClick() },
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "插入图片")
                        }
                        IconButton(
                            enabled = !state.isUploading && !state.isSubmitting,
                            onClick = { onEvent(ReplyEditorUiEvent.ClearRequested) },
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空草稿")
                        }
                        if (state.showGalleryAction) {
                            TextButton(onClick = { onEvent(ReplyEditorUiEvent.GalleryRequested) }) {
                                Text("查看图库")
                            }
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        if (state.isUploading || state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        }
                        Button(
                            enabled = !state.isUploading && !state.isSubmitting,
                            onClick = { onEvent(ReplyEditorUiEvent.Submit) },
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null)
                            Text("发布")
                        }
                    }
                }
            }
        }
    }
    if (state.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { onEvent(ReplyEditorUiEvent.ClearCancelled) },
            title = { Text("清空草稿？") },
            text = { Text("只会清除本地草稿，不会删除已上传到 V2EX 图库的图片。") },
            confirmButton = {
                Button(onClick = { onEvent(ReplyEditorUiEvent.ClearConfirmed) }) { Text("清空") }
            },
            dismissButton = {
                OutlinedButton(onClick = { onEvent(ReplyEditorUiEvent.ClearCancelled) }) { Text("取消") }
            },
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun TransparentNavigationBarWhileComposed() {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val originalNavigationBarColor = window.navigationBarColor
            val originalNavigationBarContrastEnforced = window.isNavigationBarContrastEnforced
            window.navigationBarColor = AndroidColor.TRANSPARENT
            window.isNavigationBarContrastEnforced = false

            onDispose {
                window.navigationBarColor = originalNavigationBarColor
                window.isNavigationBarContrastEnforced = originalNavigationBarContrastEnforced
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
