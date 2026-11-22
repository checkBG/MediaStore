package org.example.project.cloud.network

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.example.project.cloud.CloudCredentialsProvider
import java.util.concurrent.TimeUnit

fun buildOkHttpClient(
    credentials: CloudCredentialsProvider,
    cookieJar: CookieJar,
    enableLogging: Boolean,
    onCsrfToken: (String) -> Unit = {}
): OkHttpClient {

    val builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(AuthInterceptor(credentials))
        .addInterceptor(CsrfTokenInterceptor(onCsrfToken))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)

    if (enableLogging) {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("X-CSRF-Token")
            redactHeader("X-Email")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
        builder.addInterceptor(logging)
    }

    return builder.build()
}
