package app.mystery0.nodeflow.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class NodeFlowKoinModuleTest {
    @Test
    fun nodeFlowModulesResolveRequiredDependencies() {
        module {
            includes(nodeFlowModules)
        }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
            ),
        )
    }
}
