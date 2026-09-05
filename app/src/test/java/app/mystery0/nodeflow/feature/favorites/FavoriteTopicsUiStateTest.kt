package app.mystery0.nodeflow.feature.favorites

import androidx.paging.LoadState
import app.mystery0.nodeflow.core.common.NodeFlowException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FavoriteTopicsUiStateTest {
    @Test
    fun offersLoginForSignedOutOrExpiredSessionOnAnyPage() {
        val ready = LoadState.NotLoading(false)
        val authError = LoadState.Error(NodeFlowException(NodeFlowException.Kind.Auth, "测试登录失效"))
        assertThat(favoriteTopicsRequiresLogin(false, ready, ready)).isTrue()
        assertThat(favoriteTopicsRequiresLogin(true, authError, ready)).isTrue()
        assertThat(favoriteTopicsRequiresLogin(true, ready, authError)).isTrue()
        assertThat(favoriteTopicsRequiresLogin(true, ready, ready)).isFalse()
        assertThat(favoriteTopicsRequiresLogin(true, ready, LoadState.Error(IllegalStateException("Test error")))).isFalse()
    }
}
