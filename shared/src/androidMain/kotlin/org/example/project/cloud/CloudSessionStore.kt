package org.example.project.cloud

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CloudSession(
    val email: String = "",
    val csrfToken: String = "",
    val pageId: String? = null
) {
    val isLoggedIn: Boolean
        get() = email.isNotBlank() && csrfToken.isNotBlank()
}

/**
 * Хранит сессию Cloud Mail.ru.
 *
 * Cookies живут в CookieManager (общий с WebView),
 * email/token/pageId — в SharedPreferences.
 */
class CloudSessionStore(context: Context) : CloudCredentialsProvider {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<CloudSession> = _state.asStateFlow()

    fun save(email: String, csrfToken: String, pageId: String?) {
        val cleanEmail = email.trim()
        val cleanToken = csrfToken.trim()
        val cleanPageId = pageId?.trim()?.takeIf { it.isNotBlank() }

        prefs.edit()
            .putString(KEY_EMAIL, cleanEmail)
            .putString(KEY_TOKEN, cleanToken)
            .putString(KEY_PAGE_ID, cleanPageId)
            .apply()

        _state.value = CloudSession(
            email = cleanEmail,
            csrfToken = cleanToken,
            pageId = cleanPageId
        )
    }

    fun updateCsrfToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return

        prefs.edit().putString(KEY_TOKEN, cleanToken).apply()
        _state.value = _state.value.copy(csrfToken = cleanToken)
    }

    fun updatePageId(pageId: String?) {
        val cleanPageId = pageId?.trim()?.takeIf { it.isNotBlank() } ?: return

        prefs.edit().putString(KEY_PAGE_ID, cleanPageId).apply()
        _state.value = _state.value.copy(pageId = cleanPageId)
    }

    /** очищает prefs, состояние и cookies (removeAllCookies + flush). */
    fun clear() {
        prefs.edit().clear().apply()
        _state.value = CloudSession()

        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    override fun email(): String = _state.value.email

    override fun csrfToken(): String = _state.value.csrfToken

    override fun pageId(): String? = _state.value.pageId

    private fun load(): CloudSession {
        return CloudSession(
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            csrfToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
            pageId = prefs.getString(KEY_PAGE_ID, null)
        )
    }

    private companion object {
        const val PREFS_NAME = "cloud_session"
        const val KEY_EMAIL = "email"
        const val KEY_TOKEN = "csrf_token"
        const val KEY_PAGE_ID = "page_id"
    }
}
