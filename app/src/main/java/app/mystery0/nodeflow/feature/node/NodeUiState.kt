package app.mystery0.nodeflow.feature.node

import app.mystery0.nodeflow.core.model.Node

data class NodeUiState(
    val nodeName: String = "python",
    val node: Node? = null,
    val isPinnedHomeNode: Boolean = false,
)
