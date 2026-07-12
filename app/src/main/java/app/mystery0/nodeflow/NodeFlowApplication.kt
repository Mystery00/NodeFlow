package app.mystery0.nodeflow

import android.app.Application
import app.mystery0.nodeflow.core.crash.NodeFlowCrashHandler
import app.mystery0.nodeflow.di.nodeFlowModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class NodeFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 崩溃页进程保持最小化：不装崩溃处理器（避免崩溃页自身崩溃时死循环），也不初始化 Koin
        if (getProcessName().endsWith(":crash")) return
        NodeFlowCrashHandler.install(this)
        startKoin {
            androidContext(this@NodeFlowApplication)
            modules(nodeFlowModules)
        }
    }
}
