package org.example.project.cloud.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cloud API может вернуть новый X-CSRF-Token в ответе.
 * Сохраняем его, чтобы следующие запросы шли со свежим токеном.
 */
class CsrfTokenInterceptor(
    private val onToken: (String) -> Unit
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        response.header("X-CSRF-Token")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(onToken)

        return response
    }
}
