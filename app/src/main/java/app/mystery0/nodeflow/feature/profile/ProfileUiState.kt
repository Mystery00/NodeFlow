package app.mystery0.nodeflow.feature.profile

import app.mystery0.nodeflow.core.model.User

data class ProfileUiState(
    val username: String = "",
    val isLoading: Boolean = true,
    val user: User? = null,
    val errorMessage: String? = null,
)
