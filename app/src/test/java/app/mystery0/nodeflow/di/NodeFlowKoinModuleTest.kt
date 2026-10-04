package app.mystery0.nodeflow.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import app.mystery0.nodeflow.data.notification.UnreadNotificationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

@OptIn(KoinExperimentalAPI::class)
class NodeFlowKoinModuleTest {
    @Test
    fun nodeFlowModulesResolveRequiredDependencies() {
        module {
            includes(nodeFlowModules)
        }.verify(
            // 工厂从 SessionStore 提供会话流，store 自己拥有应用级协程作用域。
            injections = injectedParameters(
                definition<UnreadNotificationStore>(Flow::class, CoroutineScope::class),
            ),
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
            ),
        )
    }
}
