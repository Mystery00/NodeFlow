package app.mystery0.nodeflow.feature.auth

import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge

data class AuthUiState(
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
        get() = !isSubmitting && !isLoadingChallenge && !loginCompleted &&
            if (twoFactorChallenge != null) {
                twoFactorCode.length == 6 && twoFactorCode.all { it in '0'..'9' }
            } else {
                username.isNotBlank() &&
                    password.isNotEmpty() &&
                    captcha.isNotBlank() &&
                    challenge != null
            }
}
