package org.example.project.cloud.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Мост между cookies WebView и OkHttp.
 *
 * WebView после логина хранит cookies в CookieManager.
 * OkHttp читает их отсюда и отправляет в Cloud API.
 */
class WebViewCookieJar(
    private val cookieManager: CookieManager
) : CookieJar {

    /** записывает cookies в CookieManager и сбрасывает на диск. */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
        cookieManager.flush()
    }

    /** читает cookies для URL и превращает их в список Cookie (невалидные отбрасывает). */
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val rawCookies = cookieManager.getCookie(url.toString()).orEmpty()
        if (rawCookies.isBlank()) return emptyList()

        return rawCookies.split(';').mapNotNull { part ->
            Cookie.parse(url, part.trim())
        }
    }
}
