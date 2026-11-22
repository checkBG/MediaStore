package org.example.project.cloud.network

import okhttp3.Interceptor
import okhttp3.Response
import org.example.project.cloud.CloudCredentialsProvider
import java.io.IOException

/**
 * Некорректные значения в провайдере сессии:
 * перенос строки, пустой токен и т.п.
 *
 * Наследуемся от IOException, чтобы OkHttp доставил ошибку
 * в onFailure, а не уронил Dispatcher.
 */
class CloudAuthConfigException(message: String) : IOException(message)


/**
 * OkHttp-интерсептор авторизации. Работает только для хоста "cloud.mail.ru" - остальные
 * запросы пропускает без изменений.
 *
 * Для «своих» запросов добавляет:
 * @param `X-Email`,
 * @param `X-CSRF-Token`,
 * @param `User-Agent `,
 * @param `X-Page-Id`, если есть.
 * */
class AuthInterceptor(
    private val credentials: CloudCredentialsProvider
) : Interceptor {

    /**
     * проверка хоста, сборка запроса с заголовками, вызов chain.proceed.
     * */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host != CLOUD_HOST) {
            return chain.proceed(request)
        }

        val builder = request.newBuilder()
            .header("X-Email", safeHeader("X-Email", credentials.email()))
            .header("X-CSRF-Token", safeHeader("X-CSRF-Token", credentials.csrfToken()))
            .header("User-Agent", USER_AGENT)

        credentials.pageId()?.let { pageId ->
            builder.header("X-Page-Id", safeHeader("X-Page-Id", pageId))
        }

        return chain.proceed(builder.build())
    }

    /**
     * trim, проверка на пустоту и control-символы, возврат безопасного значения.
     * */
    private fun safeHeader(name: String, rawValue: String): String {
        val value = rawValue.trim()

        if (value.isEmpty()) {
            throw CloudAuthConfigException("$name пуст")
        }

        val hasControlChars = value.any { char ->
            char == '\r' || char == '\n' || char.code < 0x20 || char.code == 0x7f
        }

        if (hasControlChars) {
            throw CloudAuthConfigException(
                "$name содержит перенос строки или управляющий символ. " +
                        "Вставь только значение заголовка, без curl."
            )
        }

        return value
    }

    private companion object {
        const val CLOUD_HOST = "cloud.mail.ru"
        const val USER_AGENT = "Mediateka/1.0 (Android)"
    }
}
