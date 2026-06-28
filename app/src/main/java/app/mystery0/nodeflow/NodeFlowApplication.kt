package app.mystery0.nodeflow

import android.app.Application
import app.mystery0.nodeflow.di.nodeFlowModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class NodeFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NodeFlowApplication)
            modules(nodeFlowModules)
        }
    }
}
