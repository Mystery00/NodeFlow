package app.mystery0.nodeflow.data

import app.mystery0.nodeflow.data.auth.WebAuthRemoteDataSource
import app.mystery0.nodeflow.data.account.AccountRemoteDataSource
import app.mystery0.nodeflow.data.node.NodeLocalDataSource
import app.mystery0.nodeflow.data.node.NodeRemoteDataSource
import app.mystery0.nodeflow.data.topic.TopicLocalDataSource
import app.mystery0.nodeflow.data.topic.TopicRemoteDataSource
import app.mystery0.nodeflow.data.user.UserLocalDataSource
import app.mystery0.nodeflow.data.user.UserRemoteDataSource
import org.koin.dsl.module

val dataSourceModule = module {
    single {
        TopicRemoteDataSource(get(), get(), get())
    }

    single {
        TopicLocalDataSource(get())
    }

    single {
        NodeRemoteDataSource(get(), get(), get())
    }

    single {
        NodeLocalDataSource(get())
    }

    single {
        UserRemoteDataSource(get(), get(), get())
    }

    single {
        UserLocalDataSource(get())
    }

    single {
        WebAuthRemoteDataSource(get(), get(), get())
    }

    single {
        AccountRemoteDataSource(get(), get())
    }
}
