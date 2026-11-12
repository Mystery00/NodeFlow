package app.mystery0.nodeflow.feature.auth

import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.domain.auth.AuthRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test fun challengeRefreshIsSingleFlight() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<LoginChallenge>>()
        val repository = FakeAuthRepository().apply { challengeResult = { gate.await() } }
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
        val repository = FakeAuthRepository()
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
        val repository = FakeAuthRepository().apply { loginResult = { gate.await() } }
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
        val repository = FakeAuthRepository().apply { loginResult = { Result.success(AuthLoginResult.TwoFactorRequired(twoFactor)) } }
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

    @Test fun formattedCodeCanBePastedAndFailedVerificationCanBeRetried() = runTest(dispatcher) {
        val repository = FakeAuthRepository().apply {
            loginResult = { Result.success(AuthLoginResult.TwoFactorRequired(twoFactor)) }
            verificationResult = Result.failure(IllegalStateException("验证码无效或已过期"))
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
        assertThat(repository.lastCode).isEqualTo("123456")
        assertThat(viewModel.uiState.value.twoFactorChallenge).isEqualTo(twoFactor)
        assertThat(viewModel.uiState.value.errorMessage).isNotEmpty()
        assertThat(viewModel.uiState.value.canSubmit).isTrue()
        repository.verificationResult = Result.success(AuthSession(username = "reader"))
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

    private inner class FakeAuthRepository : AuthRepository {
        override val session = MutableStateFlow(AuthSession())
        var challengeCalls = 0
        var loginCalls = 0
        var verificationCalls = 0
        var lastCode: String? = null
        var challengeResult: suspend () -> Result<LoginChallenge> = { Result.success(challenge) }
        var loginResult: suspend () -> Result<AuthLoginResult> = { Result.success(AuthLoginResult.Completed(AuthSession(username = "reader"))) }
        var verificationResult: Result<AuthSession> = Result.success(AuthSession(username = "reader"))
        override suspend fun loginChallenge(): Result<LoginChallenge> { challengeCalls++; return challengeResult() }
        override suspend fun login(username: String, password: String, captcha: String, challenge: LoginChallenge): Result<AuthLoginResult> {
            loginCalls++
            return loginResult()
        }
        override suspend fun verifyTwoFactor(code: String, challenge: TwoFactorChallenge): Result<AuthSession> {
            verificationCalls++
            lastCode = code
            return verificationResult
        }
        override suspend fun clearSession() = Unit
    }
}
