package app.mystery0.nodeflow.feature.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.NodeChip
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.ui.ListRefreshIndicator

fun filterNodePlanes(
    planes: List<NodePlane>,
    query: String,
): List<NodePlane> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return planes
    return planes.mapNotNull { plane ->
        val matchedNodes = plane.nodes.filter { node ->
            node.name.lowercase().contains(normalizedQuery) ||
                node.title.lowercase().contains(normalizedQuery)
        }
        if (matchedNodes.isEmpty()) null else plane.copy(nodes = matchedNodes)
    }
}

fun nodePlaneSubtitle(plane: NodePlane): String {
    val count = plane.nodeCount ?: plane.nodes.size
    return "${plane.name} · $count 个节点"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeListScreen(
    state: NodeListUiState,
    onEvent: (NodeListUiEvent) -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("节点") },
                actions = {
                    IconButton(onClick = { onEvent(NodeListUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(paddingValues = paddingValues)
            state.errorMessage != null && state.planes.isEmpty() -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(NodeListUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            state.planes.isEmpty() -> EmptyContent(
                message = "暂无节点",
                paddingValues = paddingValues,
            )
            else -> NodePlaneList(
                state = state,
                onEvent = onEvent,
                onNodeClick = onNodeClick,
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun NodePlaneList(
    state: NodeListUiState,
    onEvent: (NodeListUiEvent) -> Unit,
    onNodeClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val visiblePlanes = remember(state.planes, state.query) {
        filterNodePlanes(state.planes, state.query)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 80.dp,
            ),
        ) {
            item(key = "node-search") {
                NodeSearchField(
                    query = state.query,
                    onQueryChange = { onEvent(NodeListUiEvent.QueryChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (visiblePlanes.isEmpty()) {
                item(key = "node-empty-search") {
                    EmptySearchResult(query = state.query)
                }
            } else {
                visiblePlanes.forEach { plane ->
                    item(key = "plane-header-${plane.name}") {
                        NodePlaneHeader(plane = plane)
                    }
                    // 把节点切成小块，每块一行 FlowRow 作为独立的 lazy item，
                    // 让 LazyColumn 只组合可见的 chip 行，避免单个分区一次性铺开数百个 chip
                    val chunks = plane.nodes.chunked(NODE_CHIP_CHUNK_SIZE)
                    itemsIndexed(
                        items = chunks,
                        key = { index, _ -> "plane-chips-${plane.name}-$index" },
                    ) { _, chunk ->
                        NodeChipRow(nodes = chunk, onNodeClick = onNodeClick)
                    }
                    item(key = "plane-divider-${plane.name}") {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
        ListRefreshIndicator(
            isRefreshing = state.isRefreshing,
            itemCount = state.planes.sumOf { it.nodes.size },
            topPadding = contentPadding.calculateTopPadding(),
        )
    }
}

private const val NODE_CHIP_CHUNK_SIZE = 30

@Composable
private fun NodeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Clear, contentDescription = "清空")
                }
            }
        },
        placeholder = { Text("搜索节点") },
    )
}

@Composable
private fun NodePlaneHeader(
    plane: NodePlane,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = plane.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = nodePlaneSubtitle(plane),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeChipRow(
    nodes: List<Node>,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        nodes.forEach { node ->
            NodeChip(
                title = node.title,
                onClick = { onNodeClick(node.name) },
            )
        }
    }
}

@Composable
private fun EmptySearchResult(
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "没有找到“$query”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
