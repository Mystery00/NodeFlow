package app.mystery0.nodeflow.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.ui.TopicListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    onTopicClick: (Topic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("NodeFlow") },
                actions = {
                    IconButton(onClick = { onEvent(HomeUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(paddingValues = paddingValues)
            state.errorMessage != null && state.topics.isEmpty() -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(HomeUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            state.topics.isEmpty() -> EmptyContent(
                message = "暂无主题",
                paddingValues = paddingValues,
            )
            else -> TopicList(
                topics = state.topics,
                isRefreshing = state.isRefreshing,
                onTopicClick = onTopicClick,
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun TopicList(
    topics: List<Topic>,
    isRefreshing: Boolean,
    onTopicClick: (Topic) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 80.dp,
            ),
        ) {
            items(topics, key = { it.id }) { topic ->
                TopicListItem(
                    topic = topic,
                    onClick = { onTopicClick(topic) },
                )
            }
        }
        if (isRefreshing) {
            LinearProgressIndicator(Modifier.align(Alignment.TopCenter))
        }
    }
}
