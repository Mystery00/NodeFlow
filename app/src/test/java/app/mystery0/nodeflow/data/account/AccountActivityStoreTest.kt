package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.model.AuthSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountActivityStoreTest {
    private val account = AuthSession(username = "reader", cookieHeader = "test-cookie")

    @Test
    fun latestValidResponseWinsAndUnknownOrInvalidValuesPreserveIt() = runTest {
        val store = AccountActivityStore(MutableStateFlow(account), backgroundScope)
        val older = store.beginRequest()
        val newer = store.beginRequest()
        store.update(newer, 59f)
        store.update(older, 20f)
        for (value in listOf(null, -1f, 101f, Float.NaN, Float.POSITIVE_INFINITY)) {
            store.update(store.beginRequest(), value)
            assertThat(store.activityPercent.first()).isEqualTo(59f)
        }
        store.update(store.beginRequest(), 0f)
        assertThat(store.activityPercent.first()).isEqualTo(0f)
        store.update(store.beginRequest(), 100f)
        assertThat(store.activityPercent.first()).isEqualTo(100f)
    }

    @Test
    fun accountChangesAndLogoutRejectOldRequestsEvenAfterReturningToAccount() = runTest {
        val sessions = MutableStateFlow(account)
        val store = AccountActivityStore(sessions, backgroundScope)
        val old = store.beginRequest()
        store.update(old, 59f)
        sessions.value = account.copy(username = "other")
        runCurrent()
        assertThat(store.activityPercent.first()).isNull()
        store.update(old, 80f)
        assertThat(store.activityPercent.first()).isNull()
        sessions.value = account
        runCurrent()
        store.update(old, 80f)
        assertThat(store.activityPercent.first()).isNull()
        store.update(store.beginRequest(), 60f)
        sessions.value = AuthSession()
        runCurrent()
        store.update(store.beginRequest(), 99f)
        assertThat(store.activityPercent.first()).isNull()
    }
}
