package app.mystery0.nodeflow.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dispatcherModule = module {
    single<CoroutineDispatcher>(named(IO_DISPATCHER)) {
        Dispatchers.IO
    }

    single<CoroutineDispatcher>(named(DEFAULT_DISPATCHER)) {
        Dispatchers.Default
    }
}
