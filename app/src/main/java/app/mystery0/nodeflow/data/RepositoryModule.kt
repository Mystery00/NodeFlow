package app.mystery0.nodeflow.data

import app.mystery0.nodeflow.data.auth.AuthRepositoryImpl
import app.mystery0.nodeflow.data.node.NodeRepositoryImpl
import app.mystery0.nodeflow.data.notification.NotificationRepositoryImpl
import app.mystery0.nodeflow.data.settings.SettingsRepositoryImpl
import app.mystery0.nodeflow.data.topic.TopicRepositoryImpl
import app.mystery0.nodeflow.data.user.UserRepositoryImpl
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.node.NodeRepository
import app.mystery0.nodeflow.domain.notification.NotificationRepository
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import app.mystery0.nodeflow.domain.topic.TopicRepository
import app.mystery0.nodeflow.domain.user.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTopicRepository(impl: TopicRepositoryImpl): TopicRepository

    @Binds
    @Singleton
    abstract fun bindNodeRepository(impl: NodeRepositoryImpl): NodeRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}
