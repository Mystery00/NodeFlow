package app.mystery0.nodeflow.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.ui.formatEpochSeconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.username) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(ProfileUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(paddingValues = paddingValues)
            state.errorMessage != null && state.user == null -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(ProfileUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            state.user != null -> ProfileContent(
                user = state.user,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    user: User,
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
        ProfileLine(label = "位置", value = user.location)
        ProfileLine(label = "网站", value = user.website)
        ProfileLine(label = "GitHub", value = user.github)
        ProfileLine(label = "加入时间", value = formatEpochSeconds(user.createdAtEpochSeconds))
        Text(
            text = "TODO：后续补充用户主题、收藏节点和更完整的网页登录资料解析。",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        text = "$label：$value",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
    )
}
