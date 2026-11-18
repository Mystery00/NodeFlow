package app.mystery0.nodeflow.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.mystery0.nodeflow.core.network.ech.validResolver
import app.mystery0.nodeflow.domain.settings.EchStatus

@Composable
internal fun EchSettings(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    val enabled = state.settings.echEnabled
    ListItem(
        content = { Text("尝试 ECH 直连") },
        supportingContent = { Text("连接 V2EX 时加密 TLS 握手中的域名") },
        trailingContent = { Switch(enabled, { onEvent(SettingsUiEvent.EchChanged(it)) }) },
    )
    var editing by rememberSaveable { mutableStateOf(false) }
    ListItem(
        content = { Text("备用 DNS 服务器") },
        supportingContent = { Text("仅在内置 DNS 均无法建立 ECH 连接时使用。服务器需返回含 ech 字段的 HTTPS 记录。") },
        trailingContent = { TextButton(onClick = { editing = true }) { Text(if (state.settings.echFallbackDns.isEmpty()) "设置" else "编辑") } },
    )
    if (editing) {
        var input by rememberSaveable { mutableStateOf(state.settings.echFallbackDns) }
        val valid = validResolver(input.trim())
        AlertDialog(
            onDismissRequest = { editing = false }, title = { Text("备用 DNS 服务器") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                label = { Text("DNS 地址") }, isError = !valid,
                supportingText = { Text("输入 IPv4、IPv6 或完整的 HTTPS DoH 地址。留空即移除。") }) },
            confirmButton = { TextButton(enabled = valid, onClick = {
                onEvent(SettingsUiEvent.EchDnsChanged(input)); editing = false
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
        )
    }
    if (enabled) {
        val status = state.echStatus
        ListItem(content = { Text(when (status) {
            EchStatus.Idle, EchStatus.Checking -> "正在验证 ECH 连接…"
            EchStatus.Ready -> "已通过 ECH 连接 V2EX"
            EchStatus.Unavailable -> "当前网络未能建立 ECH 连接，请检查备用 DNS 或关闭直连。"
        }, color = if (status == EchStatus.Unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
            trailingContent = { if (status != EchStatus.Checking) TextButton(onClick = { onEvent(SettingsUiEvent.CheckEch) }) { Text("重新检测") } },
        )
    }
}
