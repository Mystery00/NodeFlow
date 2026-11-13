package app.mystery0.nodeflow.feature.auth

import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.testing.FakeAuthRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val challenge = LoginChallenge("user", "password", "captcha", "test-only", "/mission/daily", "/_captcha", byteArrayOf(1))
    private val twoFactor = TwoFactorChallenge("test-only", "两步验证")

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun twoFactorRequiresSixAsciiDigits() {
        val state = AuthUiState(twoFactorChallenge = twoFactor)
        listOf("", "1", "12345", "1234567", "abcdef", "１２３４５６").forEach { code ->
            assertThat(state.copy(twoFactorCode = code).canSubmit).isFalse()
        }
        assertThat(state.copy(twoFactorCode = "123456").canSubmit).isTrue()
    }

    @Test fun loadingOrCompletedFormCannotSubmit() {
        val ready = AuthUiState(username = "reader", password = "sample", captcha = "A7K2", challenge = challenge)
        assertThat(ready.canSubmit).isTrue()
        assertThat(ready.copy(isLoadingChallenge = true).canSubmit).isFalse()
        assertThat(ready.copy(loginCompleted = true).canSubmit).isFalse()
    }

    @Test fun asciiCredentialInputKeepsPrintableAsciiAndStripsOthers() {
        assertThat(asciiCredentialInput("User_01@v2ex.com")).isEqualTo("User_01@v2ex.com")
        assertThat(asciiCredentialInput("P@ss-word!123")).isEqualTo("P@ss-word!123")
        assertThat(asciiCredentialInput("用户abc名")).isEqualTo("abc")
        assertThat(asciiCredentialInput("ｆｕｌｌ宽width")).isEqualTo("width")
        assertThat(asciiCredentialInput("emoji😀ok")).isEqualTo("emojiok")
        assertThat(asciiCredentialInput("a b\tc\nd")).isEqualTo("abcd")
    }

    @Test fun challengeRefreshIsSingleFlight() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<LoginChallenge>>()
        val repository = fakeAuthRepository().apply { challengeResult = { gate.await() } }
        val viewModel = AuthViewModel(repository)
        viewModel.onEvent(AuthUiEvent.RefreshChallenge)
        viewModel.onEvent(AuthUiEvent.RefreshChallenge)
        runCurrent()
        assertThat(repository.challengeCalls).isEqualTo(1)
        gate.complete(Result.success(challenge))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoadingChallenge).isFalse()
    }

    @Test fun refreshKeepsCredentialsAndClearsOldCaptchaBeforeLoading() = runTest(dispatcher) {
        val repository = fakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()
        fillCredentials(viewModel)
        viewModel.onEvent(AuthUiEvent.RefreshChallenge)
        assertThat(viewModel.uiState.value.username).isEqualTo("reader@example.test")
        assertThat(viewModel.uiState.value.password).isEqualTo("sample-password")
        assertThat(viewModel.uiState.value.captcha).isEmpty()
        assertThat(viewModel.uiState.value.challenge).isNull()
        assertThat(viewModel.uiState.value.canSubmit).isFalse()
        advanceUntilIdle()
    }

    @Test fun rapidSubmitAndRefreshCannotDuplicateOrChangeInFlightLogin() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<AuthLoginResult>>()
        val repository = fakeAuthRepository().apply { loginResult = { _, _, _, _ -> gate.await() } }
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()
        fillCredentials(viewModel)
        viewModel.onEvent(AuthUiEvent.Submit)
        viewModel.onEvent(AuthUiEvent.Submit)
        viewModel.onEvent(AuthUiEvent.PasswordChanged("changed"))
        viewModel.onEvent(AuthUiEvent.RefreshChallenge)
        runCurrent()
        assertThat(repository.loginCalls).isEqualTo(1)
        assertThat(repository.challengeCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.password).isEqualTo("sample-password")
        gate.complete(Result.success(AuthLoginResult.Completed(AuthSession(username = "reader"))))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loginCompleted).isTrue()
        assertThat(viewModel.uiState.value.password).isEmpty()
    }

    @Test fun enteringTwoFactorKeepsAccountContextAndDiscardsFirstStepSecrets() = runTest(dispatcher) {
        val repository = fakeAuthRepository().apply {
            loginResult = { _, _, _, _ -> Result.success(AuthLoginResult.TwoFactorRequired(twoFactor)) }
        }
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()
        fillCredentials(viewModel)
        viewModel.onEvent(AuthUiEvent.Submit)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertThat(state.username).isEqualTo("reader@example.test")
        assertThat(state.password).isEmpty()
        assertThat(state.captcha).isEmpty()
        assertThat(state.challenge).isNull()
        assertThat(state.twoFactorChallenge).isEqualTo(twoFactor)
        assertThat(state.loginCompleted).isFalse()
        viewModel.onEvent(AuthUiEvent.RefreshChallenge)
        advanceUntilIdle()
        assertThat(repository.challengeCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.twoFactorChallenge).isEqualTo(twoFactor)
    }

    @Test fun backToCredentialsClearsTwoFactorAndRefreshesChallenge() = runTest(dispatcher) {
        val repository = fakeAuthRepository().apply {
            loginResult = { _, _, _, _ -> Result.success(AuthLoginResult.TwoFactorRequired(twoFactor)) }
        }
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()
        fillCredentials(viewModel)
        viewModel.onEvent(AuthUiEvent.Submit)
        advanceUntilIdle()
        viewModel.onEvent(AuthUiEvent.TwoFactorCodeChanged("123456"))

        viewModel.onEvent(AuthUiEvent.BackToCredentials)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.twoFactorChallenge).isNull()
        assertThat(state.twoFactorCode).isEmpty()
        assertThat(state.username).isEqualTo("reader@example.test")
        assertThat(state.challenge).isEqualTo(challenge)
        assertThat(repository.challengeCalls).isEqualTo(2)
    }

    @Test fun loginFailureKeepsErrorAndRefreshesCaptchaChallenge() = runTest(dispatcher) {
        val repository = fakeAuthRepository().apply {
            loginResult = { _, _, _, _ -> Result.failure(IllegalStateException("验证码无效")) }
        }
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()
        fillCredentials(viewModel)

        viewModel.onEvent(AuthUiEvent.Submit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.errorMessage).isEqualTo("验证码无效")
        assertThat(state.captcha).isEmpty()
        assertThat(state.challenge).isEqualTo(challenge)
        assertThat(repository.challengeCalls).isEqualTo(2)
    }

    @Test fun formattedCodeCanBePastedAndFailedVerificationCanBeRetried() = runTest(dispatcher) {
        val repository = fakeAuthRepository().apply {
            loginResult = { _, _, _, _ -> Result.success(AuthLoginResult.TwoFactorRequired(twoFactor)) }
            verifyTwoFactorResult = { _, _ -> Result.failure(IllegalStateException("验证码无效或已过期")) }
        }
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()
        fillCredentials(viewModel)
        viewModel.onEvent(AuthUiEvent.Submit)
        advanceUntilIdle()
        viewModel.onEvent(AuthUiEvent.TwoFactorCodeChanged("123 456"))
        assertThat(viewModel.uiState.value.twoFactorCode).isEqualTo("123456")
        viewModel.onEvent(AuthUiEvent.Submit)
        viewModel.onEvent(AuthUiEvent.Submit)
        advanceUntilIdle()
        assertThat(repository.verificationCalls).isEqualTo(1)
        assertThat(repository.lastVerificationCode).isEqualTo("123456")
        assertThat(viewModel.uiState.value.twoFactorChallenge).isEqualTo(twoFactor)
        assertThat(viewModel.uiState.value.errorMessage).isNotEmpty()
        assertThat(viewModel.uiState.value.canSubmit).isTrue()
        repository.verifyTwoFactorResult = { _, _ -> Result.success(AuthSession(username = "reader")) }
        viewModel.onEvent(AuthUiEvent.TwoFactorCodeChanged("654321"))
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        viewModel.onEvent(AuthUiEvent.Submit)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loginCompleted).isTrue()
        assertThat(viewModel.uiState.value.twoFactorCode).isEmpty()
        assertThat(repository.loginCalls).isEqualTo(1)
    }

    private fun fillCredentials(viewModel: AuthViewModel) {
        viewModel.onEvent(AuthUiEvent.UsernameChanged("reader@example.test"))
        viewModel.onEvent(AuthUiEvent.PasswordChanged("sample-password"))
        viewModel.onEvent(AuthUiEvent.CaptchaChanged("A7K2"))
    }

    private fun fakeAuthRepository() = FakeAuthRepository().apply {
        challengeResult = { Result.success(challenge) }
        loginResult = { _, _, _, _ -> Result.success(AuthLoginResult.Completed(AuthSession(username = "reader"))) }
        verifyTwoFactorResult = { _, _ -> Result.success(AuthSession(username = "reader")) }
    }
}
