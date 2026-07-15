package app.mystery0.nodeflow.data

import app.mystery0.nodeflow.core.common.IO_DISPATCHER
import app.mystery0.nodeflow.data.account.AccountOverviewRepositoryImpl
import app.mystery0.nodeflow.data.auth.AuthRepositoryImpl
import app.mystery0.nodeflow.data.node.NodeRepositoryImpl
import app.mystery0.nodeflow.data.notification.NotificationRepositoryImpl
import app.mystery0.nodeflow.data.settings.SettingsRepositoryImpl
import app.mystery0.nodeflow.data.topic.TopicRepositoryImpl
import app.mystery0.nodeflow.data.user.UserRepositoryImpl
import app.mystery0.nodeflow.domain.account.AccountOverviewRepository
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.node.NodeRepository
import app.mystery0.nodeflow.domain.notification.NotificationRepository
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import app.mystery0.nodeflow.domain.topic.TopicRepository
import app.mystery0.nodeflow.domain.user.UserRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule = module {
    single<TopicRepository> {
        TopicRepositoryImpl(get(), get(), get(named(IO_DISPATCHER)))
    }

    single<NodeRepository> {
        NodeRepositoryImpl(get(), get(), get(), get(named(IO_DISPATCHER)))
    }

    single<UserRepository> {
        UserRepositoryImpl(get(), get(), get(named(IO_DISPATCHER)))
    }

    single<SettingsRepository> {
        SettingsRepositoryImpl(get(), get(), get(), get())
    }

    single<AuthRepository> {
        AuthRepositoryImpl(get(), get(), get(), get(named(IO_DISPATCHER)))
    }

    single<AccountOverviewRepository> {
        AccountOverviewRepositoryImpl(get(), get(named(IO_DISPATCHER)))
    }

    single<NotificationRepository> {
        NotificationRepositoryImpl(get(), get(), get(named(IO_DISPATCHER)))
    }
}
