package com.wordforge.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SafeBrowsingResponse
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import java.io.ByteArrayInputStream
import org.json.JSONObject

/**
 * Displays a generated exercise session in a fixed, app-owned HTML renderer.
 *
 * [sessionJson] is encoded as data before being passed to the page's fixed
 * `renderSession` function. Model output is never evaluated as HTML or
 * JavaScript. The only JavaScript bridge accepts a bounded answer-state snapshot
 * so an Activity recreation can restore in-progress work; it performs no native action.
 */
@Composable
fun ExerciseWebView(
    sessionJson: String,
    modifier: Modifier = Modifier,
    restoredStateJson: String = "",
    onStateChange: (String) -> Unit = {},
) {
    val holder = remember { ExerciseWebViewHolder() }
    val currentOnStateChange = rememberUpdatedState(onStateChange)

    DisposableEffect(holder) {
        onDispose {
            holder.webView?.dispose()
            holder.webView = null
        }
    }

    AndroidView(
        factory = { context ->
            TrustedExerciseWebView(context) { stateJson ->
                currentOnStateChange.value(stateJson)
            }.also { webView ->
                holder.webView = webView
            }
        },
        update = { webView ->
            webView.submitSession(sessionJson, restoredStateJson)
        },
        modifier = modifier,
    )
}

private class ExerciseWebViewHolder {
    var webView: TrustedExerciseWebView? = null
}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
private class TrustedExerciseWebView(
    context: Context,
    private val onStateChange: (String) -> Unit,
) : WebView(context) {
    private var pageReady = false
    private var pendingSessionJson = ""
    private var pendingStateJson = ""
    private var lastRenderedSessionJson: String? = null
    private var disposed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateBridge = ExerciseStateBridge(::acceptState)

    init {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        configureSettings()
        configureClients()
        addJavascriptInterface(stateBridge, STATE_BRIDGE_NAME)
        setDownloadListener { _, _, _, _, _ -> Unit }

        WebView.startSafeBrowsing(context.applicationContext) { _ -> Unit }

        loadUrl(EXERCISE_ASSET_URL)
    }

    fun submitSession(sessionJson: String, restoredStateJson: String) {
        pendingSessionJson = sessionJson
        pendingStateJson = restoredStateJson
        renderPendingSession()
    }

    fun dispose() {
        disposed = true
        pageReady = false
        stopLoading()
        removeJavascriptInterface(STATE_BRIDGE_NAME)
        webChromeClient = null
        webViewClient = WebViewClient()
        clearHistory()
        removeAllViews()
        destroy()
    }

    @Suppress("DEPRECATION")
    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
            domStorageEnabled = false
            databaseEnabled = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = true
            saveFormData = false
            builtInZoomControls = false
            displayZoomControls = false
            defaultTextEncodingName = Charsets.UTF_8.name()
            safeBrowsingEnabled = true
        }
    }

    private fun configureClients() {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageReady = false
                lastRenderedSessionJson = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == EXERCISE_ASSET_URL) {
                    pageReady = true
                    renderPendingSession()
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val requestedUrl = request?.url ?: return true
                return !request.isForMainFrame || !requestedUrl.isExerciseAsset()
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url == null || !url.toUri().isExerciseAsset()
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                return if (request?.url?.isExerciseAsset() == true) null else blockedResponse()
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                return if (url != null && url.toUri().isExerciseAsset()) {
                    null
                } else {
                    blockedResponse()
                }
            }

            override fun onSafeBrowsingHit(
                view: WebView?,
                request: WebResourceRequest?,
                threatType: Int,
                callback: SafeBrowsingResponse?,
            ) {
                callback?.backToSafety(true)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean = false

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                return false
            }
        }
    }

    private fun renderPendingSession() {
        val sessionJson = pendingSessionJson
        if (!pageReady || sessionJson == lastRenderedSessionJson) return

        val base64Payload = Base64.encodeToString(
            sessionJson.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        val encodedLiteral = JSONObject.quote(base64Payload)
        val stateScript = pendingStateJson
            .takeIf { it.length in 1..MAX_STATE_LENGTH }
            ?.let { stateJson ->
                val statePayload = Base64.encodeToString(
                    stateJson.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                "window.restoreSessionState(${JSONObject.quote(statePayload)});"
            }
            .orEmpty()
        evaluateJavascript(
            "window.renderSession($encodedLiteral);$stateScript",
            null,
        )
        lastRenderedSessionJson = sessionJson
    }

    private fun acceptState(stateJson: String) {
        if (disposed || stateJson.length !in 1..MAX_STATE_LENGTH) return
        mainHandler.post {
            if (!disposed) onStateChange(stateJson)
        }
    }
}

private class ExerciseStateBridge(
    private val onStateChange: (String) -> Unit,
) {
    @JavascriptInterface
    fun saveState(stateJson: String) {
        onStateChange(stateJson)
    }
}

private fun Uri.isExerciseAsset(): Boolean =
    scheme == "file" && host.isNullOrEmpty() && path == EXERCISE_ASSET_PATH

private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    Charsets.UTF_8.name(),
    403,
    "Blocked",
    mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream(ByteArray(0)),
)

private const val EXERCISE_ASSET_PATH = "/android_asset/exercise_session.html"
private const val EXERCISE_ASSET_URL = "file://$EXERCISE_ASSET_PATH"
private const val STATE_BRIDGE_NAME = "WordForgeSessionState"
private const val MAX_STATE_LENGTH = 32 * 1024
