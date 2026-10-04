package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.model.AuthSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnreadNotificationStoreTest {
    private val account = AuthSession(username = "reader", cookieHeader = "test-cookie")

    @Test
    fun newerSuccessfulRequestWinsAndUnknownCountPreservesValue() = runTest {
        val store = UnreadNotificationStore(MutableStateFlow(account), backgroundScope)
        val older = store.beginRequest()
        val newer = store.beginRequest()
        store.update(newer, 0)
        store.update(older, 8)
        store.update(store.beginRequest(), null)
        assertThat(store.unreadCount.first()).isEqualTo(0)
        store.update(store.beginRequest(), 3)
        assertThat(store.unreadCount.first()).isEqualTo(3)
    }

    @Test
    fun accountChangeClearsCountAndRejectsPreviousAccountResponse() = runTest {
        val session = MutableStateFlow(account)
        val store = UnreadNotificationStore(session, backgroundScope)
        val oldRequest = store.beginRequest()
        store.update(oldRequest, 7)
        session.value = account.copy(username = "other")
        runCurrent()
        assertThat(store.unreadCount.first()).isNull()
        store.update(oldRequest, 9)
        assertThat(store.unreadCount.first()).isNull()
        session.value = account
        runCurrent()
        store.update(oldRequest, 9)
        assertThat(store.unreadCount.first()).isNull()
    }

    @Test
    fun signedOutRequestsCannotPublishAndLogoutClearsCount() = runTest {
        val session = MutableStateFlow(account)
        val store = UnreadNotificationStore(session, backgroundScope)
        store.update(store.beginRequest(), 7)
        session.value = AuthSession()
        runCurrent()
        store.update(store.beginRequest(), 4)
        assertThat(store.unreadCount.first()).isNull()
    }
}
