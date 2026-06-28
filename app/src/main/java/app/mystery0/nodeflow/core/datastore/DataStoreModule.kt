package app.mystery0.nodeflow.core.datastore

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataStoreModule = module {
    single {
        SessionStore(androidContext())
    }

    single {
        SettingsStore(androidContext())
    }
}
