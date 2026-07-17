package app.mystery0.nodeflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.BuildConfig
import app.mystery0.nodeflow.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onEvent(SettingsUiEvent.MessageShown)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    onBackClick?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            SettingsSectionTitle("外观")
            ListItem(
                headlineContent = { Text("深色模式") },
                supportingContent = {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { onEvent(SettingsUiEvent.ThemeModeChanged(mode)) },
                                label = { Text(mode.displayName) },
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("动态颜色") },
                supportingContent = { Text("Android 12 及以上使用系统取色") },
                trailingContent = {
                    Switch(
                        checked = state.settings.dynamicColor,
                        onCheckedChange = { onEvent(SettingsUiEvent.DynamicColorChanged(it)) },
                    )
                },
            )
            HorizontalDivider()
            SettingsSectionTitle("内容浏览")
            var showAddImageHostDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("自定义图床域名") },
                supportingContent = {
                    Text("命中这些域名的链接将尝试按图片加载")
                },
                trailingContent = {
                    Button(onClick = { showAddImageHostDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("添加")
                    }
                },
            )
            state.settings.customImageHosts.forEach { host ->
                ListItem(
                    headlineContent = { Text(host) },
                    trailingContent = {
                        IconButton(
                            onClick = { onEvent(SettingsUiEvent.RemoveCustomImageHost(host)) },
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除 $host")
                        }
                    },
                )
            }
            if (showAddImageHostDialog) {
                AddImageHostDialog(
                    onConfirm = { input ->
                        onEvent(SettingsUiEvent.AddCustomImageHost(input))
                        showAddImageHostDialog = false
                    },
                    onDismiss = { showAddImageHostDialog = false },
                )
            }
            HorizontalDivider()
            SettingsSectionTitle("数据")
            ListItem(
                headlineContent = { Text("清除缓存") },
                supportingContent = { Text("清除主题、节点和用户缓存") },
                trailingContent = {
                    Button(
                        enabled = !state.isClearingCache,
                        onClick = { onEvent(SettingsUiEvent.ClearCache) },
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                        Text(if (state.isClearingCache) "清理中" else "清除")
                    }
                },
            )
            HorizontalDivider()
            SettingsSectionTitle("关于")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "NodeFlow",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("现代 Android V2EX 第三方客户端。")
                Text("开源说明：本项目从零实现，遵循 Apache License 2.0 发布。")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "License：Apache License 2.0。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddImageHostDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加图床域名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输入域名（如 img.example.com），将同时匹配其子域名。")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text("example.com") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank(),
                onClick = { onConfirm(input) },
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.System -> "跟随系统"
        ThemeMode.Light -> "浅色"
        ThemeMode.Dark -> "深色"
    }
