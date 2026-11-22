package org.example.project.di

import android.content.pm.ApplicationInfo
import android.webkit.CookieManager
import okhttp3.CookieJar
import org.example.project.cloud.CloudCredentialsProvider
import org.example.project.cloud.CloudMailRepository
import org.example.project.cloud.CloudRepository
import org.example.project.cloud.CloudSessionStore
import org.example.project.cloud.network.CloudMailApi
import org.example.project.cloud.network.WebViewCookieJar
import org.example.project.cloud.network.buildOkHttpClient
import org.example.project.cloud.network.buildRetrofit
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit

val networkModule: Module = module {

    single { CloudSessionStore(androidContext()) }

    single<CloudCredentialsProvider> { get<CloudSessionStore>() }

    single<CookieJar> {
        WebViewCookieJar(CookieManager.getInstance())
    }

    single {
        val appInfo = androidContext().applicationInfo
        val isDebug = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        buildOkHttpClient(
            credentials = get(),
            cookieJar = get(),
            enableLogging = isDebug,
            onCsrfToken = get<CloudSessionStore>()::updateCsrfToken
        )
    }

    single { buildRetrofit(get()) }

    single<CloudMailApi> {
        get<Retrofit>().create(CloudMailApi::class.java)
    }

    single<CloudRepository> {
        CloudMailRepository(
            api = get(),
            credentials = get()
        )
    }
}
