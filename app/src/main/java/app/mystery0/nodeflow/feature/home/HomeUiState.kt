package app.mystery0.nodeflow.feature.home

import app.mystery0.nodeflow.core.model.Topic

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val topics: List<Topic> = emptyList(),
    val errorMessage: String? = null,
)
