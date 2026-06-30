package app.mystery0.nodeflow.feature.account

import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.User

data class AccountUiState(
    val session: AuthSession = AuthSession(),
    val user: User? = null,
    val overview: AccountOverview? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isLoggedIn: Boolean
        get() = !session.username.isNullOrBlank() && !session.cookieHeader.isNullOrBlank()
}
