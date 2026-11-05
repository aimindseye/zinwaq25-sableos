package org.sableos.browser.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.sableos.browser.core.BrowserText.t
import org.sableos.browser.core.UrlInput
import java.net.URISyntaxException

/**
 * The page: Android's WebView (the same Chromium engine as Chrome, updated by the system), set up
 * for privacy. No file or content access, no third-party cookies, no mixed content, Safe Browsing
 * on, one window, media starts only on a tap, location never shared. Links to other schemes (tel:,
 * mailto:, intent:) go to the app that handles them; downloads go to Android's download manager.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createBrowserWebView(
    context: Context,
    state: BrowserState,
    onScale: (Float) -> Unit,
    fileChooser: FileChooser,
): WebView =
    WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = BrowserClient(state, onScale)
        webChromeClient = BrowserChrome(state, fileChooser)
        setDownloadListener { url, userAgent, disposition, mimeType, _ ->
            download(context, state, Download(url, userAgent, disposition, mimeType))
        }
    }

private class BrowserClient(
    private val state: BrowserState,
    private val onScale: (Float) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url.toString()
        if (UrlInput.isWeb(url)) return false
        openOutside(view, state, url)
        return true
    }

    override fun onPageStarted(
        view: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {
        state.url = url
        state.failed = false
        state.editing = false
    }

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        state.url = url
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) state.failed = true
    }

    override fun onScaleChanged(
        view: WebView,
        oldScale: Float,
        newScale: Float,
    ) {
        onScale(newScale)
    }
}

private class BrowserChrome(
    private val state: BrowserState,
    private val fileChooser: FileChooser,
) : WebChromeClient() {
    override fun onProgressChanged(
        view: WebView,
        newProgress: Int,
    ) {
        state.progress = newProgress
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = fileChooser.choose(fileChooserParams.createIntent(), filePathCallback)
}

/** A link the page cannot open itself: the app for its scheme, or an intent: link's own fallback page. */
private fun openOutside(
    view: WebView,
    state: BrowserState,
    url: String,
) {
    val intent =
        try {
            if (url.startsWith("intent:")) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    // A web page may only start what a browsable link could.
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
        } catch (e: URISyntaxException) {
            Log.i(TAG, "bad intent link", e)
            null
        }
    try {
        intent?.let { view.context.startActivity(it) } ?: run { state.status = t("no_app_for_link") }
    } catch (e: ActivityNotFoundException) {
        Log.i(TAG, "no app for link", e)
        val fallback = intent?.getStringExtra(FALLBACK_URL)?.takeIf(UrlInput::isWeb)
        if (fallback != null) view.loadUrl(fallback) else state.status = t("no_app_for_link")
    }
}

/** What the page asked to download, as WebView's DownloadListener gives it. */
private data class Download(
    val url: String,
    val userAgent: String,
    val disposition: String?,
    val mimeType: String?,
)

private fun download(
    context: Context,
    state: BrowserState,
    download: Download,
) {
    val url = download.url
    val mimeType = download.mimeType
    val name = URLUtil.guessFileName(url, download.disposition, mimeType)
    state.status =
        try {
            val request =
                DownloadManager
                    .Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", download.userAgent)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            t("downloading", name)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "download refused", e)
            t("download_failed")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "no downloads folder", e)
            t("download_failed")
        }
}

private const val TAG = "SableBrowser"
private const val FALLBACK_URL = "browser_fallback_url"
