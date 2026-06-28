package app.mystery0.nodeflow.feature.node

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic

data class NodeUiState(
    val nodeName: String = "python",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val node: Node? = null,
    val topics: List<Topic> = emptyList(),
    val errorMessage: String? = null,
)
