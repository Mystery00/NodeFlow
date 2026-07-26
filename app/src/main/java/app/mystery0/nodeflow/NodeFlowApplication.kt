package app.mystery0.nodeflow

import android.app.Application
import app.mystery0.nodeflow.core.crash.NodeFlowCrashHandler
import app.mystery0.nodeflow.core.datastore.SettingsStore
import app.mystery0.nodeflow.core.notification.NotificationScheduler
import app.mystery0.nodeflow.di.nodeFlowModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class NodeFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName().endsWith(":crash")) return
        NodeFlowCrashHandler.install(this)
        startKoin {
            androidContext(this@NodeFlowApplication)
            modules(nodeFlowModules)
        }
        syncNotificationSchedule()
    }

    private fun syncNotificationSchedule() {
        val settingsStore: SettingsStore = GlobalContext.get().get()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = settingsStore.settings.first()
            NotificationScheduler.updateSchedule(
                this@NodeFlowApplication,
                settings.notificationReminder
            )
        }
    }
}
