package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        AuthInterceptor(get())
    }

    single {
        V2exCookieJar()
    }

    single {
        UserAgentInterceptor()
    }

    single {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
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
}
