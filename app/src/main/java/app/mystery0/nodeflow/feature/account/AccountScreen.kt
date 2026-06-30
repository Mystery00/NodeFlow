package app.mystery0.nodeflow.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.ui.formatEpochSeconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    onEvent: (AccountUiEvent) -> Unit,
    onSettingsClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = { onEvent(AccountUiEvent.Refresh) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            !state.isLoggedIn -> SignedOutContent(
                onLoginClick = onLoginClick,
                paddingValues = paddingValues,
            )
            state.isLoading && state.user == null -> LoadingContent(paddingValues = paddingValues)
            state.errorMessage != null && state.user == null -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(AccountUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            state.user != null -> AccountContent(
                user = state.user,
                onLogoutClick = { onEvent(AccountUiEvent.Logout) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
            )
            else -> EmptyContent(
                message = "暂无用户信息",
                paddingValues = paddingValues,
            )
        }
    }
}

@Composable
private fun SignedOutContent(
    onLoginClick: () -> Unit,
    paddingValues: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "尚未登录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "登录后可以查看当前用户信息，并为后续回复、发帖和通知功能提供会话。",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLoginClick) {
            Text("登录")
        }
    }
}

@Composable
private fun AccountContent(
    user: User,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(
            avatarUrl = user.avatarUrl,
            username = user.username,
            size = 88.dp,
        )
        Text(
            text = user.username,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        user.tagline?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        user.bio?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AccountLine(label = "位置", value = user.location)
        AccountLine(label = "网站", value = user.website)
        AccountLine(label = "GitHub", value = user.github)
        AccountLine(label = "加入时间", value = formatEpochSeconds(user.createdAtEpochSeconds))
        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("退出登录")
        }
    }
}

@Composable
private fun AccountLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        text = "$label：$value",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
    )
}
