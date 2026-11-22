package org.example.project

import android.annotation.SuppressLint
import android.os.Message
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.project.cloud.CloudSessionStore
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudLoginScreen(
    sessionStore: CloudSessionStore,
    onClose: () -> Unit
) {
    val session by sessionStore.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.isLoggedIn) {
        if (session.isLoggedIn) {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    val bridge = remember(sessionStore) {
        LoginBridge { raw ->
            val token = extractToken(raw)
            if (token != null) {
                val email = sessionStore.email().ifBlank { emailFromCookies() }
                if (email.isNotBlank()) {
                    sessionStore.save(email, token, sessionStore.pageId())
                    CookieManager.getInstance().flush()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вход в облако") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                    WebView(context).apply {
                        webView = this

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 7) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/152.0.0.0 Mobile Safari/537.36"

                        WebView.setWebContentsDebuggingEnabled(true)

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(bridge, "AndroidBridge")

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val main = view ?: return false
                                val popup = WebView(main.context)
                                Log.d("CloudLogin", "onCreateWindow")

                                popup.settings.javaScriptEnabled = true
                                popup.settings.domStorageEnabled = true
                                popup.settings.userAgentString = main.settings.userAgentString
                                CookieManager.getInstance()
                                    .setAcceptThirdPartyCookies(popup, true)
                                popup.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        Log.d("CloudLogin", "popup url: $url")
                                        main.loadUrl(url)
                                        popup.destroy()
                                        return true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        Log.d("CloudLogin", "popup finish: $url")
                                        if (!url.isNullOrBlank() && url != "about:blank") {
                                            main.loadUrl(url)
                                            popup.destroy()
                                        }
                                    }
                                }

                                val transport =
                                    resultMsg?.obj as? WebView.WebViewTransport
                                        ?: run {
                                            popup.destroy()
                                            return false
                                        }

                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }

                            override fun onConsoleMessage(
                                consoleMessage: ConsoleMessage
                            ): Boolean {
                                Log.d(
                                    "CloudLogin",
                                    "console: ${consoleMessage.message()} " +
                                            "(${consoleMessage.lineNumber()})"
                                )
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val headers = request?.requestHeaders.orEmpty()
                                val token = header(headers, "X-CSRF-Token")
                                val email = header(headers, "X-Email")
                                val pageId = header(headers, "X-Page-Id")

                                if (!pageId.isNullOrBlank()) {
                                    sessionStore.updatePageId(pageId)
                                }

                                if (!token.isNullOrBlank() && !email.isNullOrBlank()) {
                                    sessionStore.save(email, token, pageId)
                                    CookieManager.getInstance().flush()
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                pageError = null
                                Log.d("CloudLogin", "start: $url")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                Log.d("CloudLogin", "finish: $url")

                                if (url?.startsWith("https://account.mail.ru/login") == true) {
                                    // ?fail=1 нужен, чтобы открылась старая форма входа,
                                    // но вместе с ней показывается старая ошибка. Убираем её.
                                    view?.evaluateJavascript(
                                        """document.querySelectorAll('p[class*="error-"]').forEach(function(e){e.style.display='none';});""",
                                        null
                                    )
                                }

                                val loggedInRedirect =
                                    url?.startsWith("https://auth.mail.ru/inbox") == true ||
                                            url?.startsWith("https://e.mail.ru/inbox") == true ||
                                            url?.startsWith("https://touch.mail.ru/") == true ||
                                            (url?.startsWith("https://account.mail.ru/") == true &&
                                                    !url.contains("/login"))

                                if (loggedInRedirect) {
                                    view?.loadUrl("https://cloud.mail.ru/")
                                    return
                                }

                                if (url?.startsWith("https://cloud.mail.ru") == true &&
                                    !sessionStore.state.value.isLoggedIn
                                ) {
                                    view?.let(::requestToken)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageError = "${error?.errorCode}: ${error?.description}"
                                    Log.e("CloudLogin", "error: $pageError")
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                if (request?.isForMainFrame == true &&
                                    (errorResponse?.statusCode ?: 0) >= 400
                                ) {
                                    pageError = "HTTP ${errorResponse?.statusCode}"
                                    Log.e("CloudLogin", "http error: $pageError")
                                }
                            }
                        }

                        loadUrl("https://account.mail.ru/login?fail=1")
                    }
                }
                )
            }

            pageError?.let { error ->
                Text(
                    text = "Ошибка загрузки: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "Войди в аккаунт, затем нажми «Готово»",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Button(
                onClick = { webView?.let(::requestToken) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Готово")
            }
        }
    }
}

private class LoginBridge(
    private val onToken: (String) -> Unit
) {
    @JavascriptInterface
    fun onToken(raw: String) {
        onToken(raw)
    }

    @JavascriptInterface
    fun onError(message: String) {
        // Ошибку покажет состояние сессии: пользователь может нажать «Готово» ещё раз.
    }
}

private fun header(
    headers: Map<String, String>,
    name: String
): String? = headers.entries
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value

private fun requestToken(webView: WebView) {
    val host = webView.url?.let { android.net.Uri.parse(it).host }

    if (host != "cloud.mail.ru") {
        webView.loadUrl("https://cloud.mail.ru/")
        return
    }

    webView.evaluateJavascript(
        """
        fetch('/api/v2/tokens/csrf', { credentials: 'include' })
          .then(r => r.text())
          .then(t => AndroidBridge.onToken(t))
          .catch(e => AndroidBridge.onError(String(e)));
        """.trimIndent(),
        null
    )
}

private fun emailFromCookies(): String {
    val cookies = CookieManager.getInstance()
        .getCookie("https://cloud.mail.ru")
        .orEmpty()

    return Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        .find(cookies)
        ?.value
        .orEmpty()
}

private fun extractToken(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val fromJson = runCatching {
        val json = JSONObject(text)
        json.optString("token").takeIf { it.isNotBlank() }
            ?: json.optString("csrf").takeIf { it.isNotBlank() }
            ?: json.optJSONObject("body")?.optString("token")?.takeIf { it.isNotBlank() }
            ?: json.optString("body").takeIf { it.isNotBlank() }
    }.getOrNull()

    if (fromJson != null) return fromJson

    return text.takeIf {
        it.length in 10..200 && !it.contains('<') && !it.contains(' ')
    }
}
