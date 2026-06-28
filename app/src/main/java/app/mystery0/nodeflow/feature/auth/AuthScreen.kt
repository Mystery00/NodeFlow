package app.mystery0.nodeflow.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("登录状态", style = MaterialTheme.typography.titleLarge)
        Text("用户名：${state.session.username ?: "未登录"}")
        Text("Token：${if (state.session.personalAccessToken.isNullOrBlank()) "未保存" else "已保存"}")
        Text("Cookie：${if (state.session.cookieHeader.isNullOrBlank()) "未保存" else "已保存"}")
        Text("TODO：后续实现网页登录流程、once 获取、回复和发帖能力。")
        Button(onClick = { onEvent(AuthUiEvent.ClearSession) }) {
            Text("清除登录态")
        }
    }
}
