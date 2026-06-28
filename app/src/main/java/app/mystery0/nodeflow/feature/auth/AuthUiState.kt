package app.mystery0.nodeflow.feature.auth

import app.mystery0.nodeflow.core.model.AuthSession

data class AuthUiState(
    val session: AuthSession = AuthSession(),
)
