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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
