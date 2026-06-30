package app.mystery0.nodeflow.feature.auth

import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge

data class AuthUiState(
    val session: AuthSession = AuthSession(),
    val challenge: LoginChallenge? = null,
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val twoFactorChallenge: TwoFactorChallenge? = null,
    val twoFactorCode: String = "",
    val isLoadingChallenge: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val loginCompleted: Boolean = false,
) {
    val canSubmit: Boolean
        get() = if (twoFactorChallenge != null) {
            twoFactorCode.isNotBlank() && !isSubmitting
        } else {
            username.isNotBlank() &&
                password.isNotBlank() &&
                captcha.isNotBlank() &&
                challenge != null &&
                !isSubmitting
        }
}
