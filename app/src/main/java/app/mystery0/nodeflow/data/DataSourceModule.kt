package app.mystery0.nodeflow.data

import app.mystery0.nodeflow.core.datastore.SessionStore
import app.mystery0.nodeflow.core.datastore.SettingsStore
import app.mystery0.nodeflow.core.notification.NotificationReminderChecker
import app.mystery0.nodeflow.core.notification.NotificationReminderEnabledProvider
import app.mystery0.nodeflow.core.notification.NotificationUnreadCountProvider
import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import app.mystery0.nodeflow.data.account.AccountRemoteDataSource
import app.mystery0.nodeflow.data.auth.WebAuthRemoteDataSource
import app.mystery0.nodeflow.data.node.NodeLocalDataSource
import app.mystery0.nodeflow.data.node.NodeRemoteDataSource
import app.mystery0.nodeflow.data.notification.NotificationReminderRemoteDataSource
import app.mystery0.nodeflow.data.notification.NotificationRemoteDataSource
import app.mystery0.nodeflow.data.notification.UnreadNotificationStore
import app.mystery0.nodeflow.data.reply.AndroidImageContentReader
import app.mystery0.nodeflow.data.reply.ImageContentReader
import app.mystery0.nodeflow.data.reply.ReplyDraftLocalDataSource
import app.mystery0.nodeflow.data.reply.ReplyRemoteDataSource
import app.mystery0.nodeflow.data.reply.V2exImageTransportImpl
import app.mystery0.nodeflow.data.topic.DefaultImageDownloader
import app.mystery0.nodeflow.data.topic.ImageDownloader
import app.mystery0.nodeflow.data.topic.TopicLocalDataSource
import app.mystery0.nodeflow.data.topic.TopicRemoteDataSource
import app.mystery0.nodeflow.data.user.UserLocalDataSource
import app.mystery0.nodeflow.data.user.UserRemoteDataSource
import app.mystery0.nodeflow.domain.notification.UnreadNotificationRepository
import app.mystery0.nodeflow.imagehosting.imgur.ImgurHttpImageHostAdapter
import app.mystery0.nodeflow.imagehosting.registry.DefaultImageHostRegistry
import app.mystery0.nodeflow.imagehosting.registry.ImageHostRegistry
import app.mystery0.nodeflow.imagehosting.v2ex.V2exImageHostAdapter
import app.mystery0.nodeflow.imagehosting.v2ex.V2exImageTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.core.qualifier.named
import app.mystery0.nodeflow.core.common.IO_DISPATCHER
import okhttp3.OkHttpClient

val dataSourceModule = module {
    single { UnreadNotificationStore(get<SessionStore>().session) }
    single<UnreadNotificationRepository> { get<UnreadNotificationStore>() }

    single {
        TopicRemoteDataSource(get(), get(), get(), get(), get())
    }

    single {
        TopicLocalDataSource(get())
    }

    single {
        NodeRemoteDataSource(get(), get(), get(), get())
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
        AccountRemoteDataSource(get(), get(), get())
    }

    single {
        NotificationRemoteDataSource(get(), get(), get())
    }

    single {
        NotificationReminderRemoteDataSource(get(), get())
    }

    single<NotificationUnreadCountProvider> {
        get<NotificationReminderRemoteDataSource>()
    }

    single<NotificationReminderEnabledProvider> {
        val settingsStore = get<SettingsStore>()
        NotificationReminderEnabledProvider {
            settingsStore.settings.first().notificationReminder
        }
    }

    single {
        NotificationReminderChecker(
            unreadCountProvider = get<NotificationUnreadCountProvider>(),
            store = get<EncryptedKeyValueStore>(),
            reminderEnabled = get<SettingsStore>().settings.map { it.notificationReminder },
        )
    }

    single { ReplyRemoteDataSource(get(), get()) }
    single<V2exImageTransport> { V2exImageTransportImpl(get()) }
    single { V2exImageHostAdapter(get()) }
    single { ImgurHttpImageHostAdapter() }
    single<ImageHostRegistry> {
        DefaultImageHostRegistry(
            listOf(get<V2exImageHostAdapter>(), get<ImgurHttpImageHostAdapter>()),
        )
    }
    single { ReplyDraftLocalDataSource(get()) }
    single<ImageContentReader> { AndroidImageContentReader(androidContext().contentResolver) }
    // 图片下载客户端不携带 V2EX 的认证拦截器或 Cookie。
    single(named("imageDownloadClient")) { OkHttpClient.Builder().build() }
    single<ImageDownloader> {
        DefaultImageDownloader(androidContext(), get(named("imageDownloadClient")), get(named(IO_DISPATCHER)))
    }
}

