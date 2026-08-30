package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import org.koin.core.qualifier.named
import retrofit2.Retrofit

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        AuthInterceptor(get(), get())
    }

    single<V2exCookieStorage> {
        EncryptedV2exCookieStorage(get())
    }

    single {
        V2exCookieJar(get())
    }

    single {
        UserAgentInterceptor()
    }

    single {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            redactQueryParams("once")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .cookieJar(get<V2exCookieJar>())
            .addInterceptor(get<UserAgentInterceptor>())
            .addInterceptor(get<AuthInterceptor>())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl("https://www.v2ex.com/")
            .client(get<OkHttpClient>())
            .build()
    }

    single {
        get<Retrofit>().create(V2exRawApi::class.java)
    }

    single(named("v2exWriteClient")) {
        get<OkHttpClient>().newBuilder()
            .retryOnConnectionFailure(false)
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl("https://www.v2ex.com/")
            .client(get(named("v2exWriteClient")))
            .build()
            .create(V2exWriteApi::class.java)
    }
}
