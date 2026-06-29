package app.mystery0.nodeflow.feature.node

import app.mystery0.nodeflow.core.model.NodePlane

data class NodeListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val query: String = "",
    val planes: List<NodePlane> = emptyList(),
    val errorMessage: String? = null,
)
