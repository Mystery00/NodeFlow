package app.mystery0.nodeflow.feature.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotificationScreen(
    state: NotificationUiState,
    onEvent: (NotificationUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("通知", style = MaterialTheme.typography.titleLarge)
        Text("第一阶段预留通知入口，后台轮询和系统通知将在 WorkManager 接入后实现。")
        Text("当前通知数量：${state.notifications.size}")
    }
}
