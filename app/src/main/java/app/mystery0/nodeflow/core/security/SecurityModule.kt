package app.mystery0.nodeflow.core.security

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val securityModule = module {
    single<StringKeyValueStorage> {
        SharedPreferencesStringKeyValueStorage(
            androidContext().getSharedPreferences(
                "nodeflow_secure_storage",
                Context.MODE_PRIVATE,
            ),
        )
    }
    single<SecretCipher> { AndroidKeystoreSecretCipher() }
    single { EncryptedKeyValueStore(get(), get()) }
}
