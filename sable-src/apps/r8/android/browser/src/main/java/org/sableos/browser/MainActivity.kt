package org.sableos.browser

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.sableos.browser.core.BrowserText.t
import org.sableos.browser.core.UrlInput
import org.sableos.browser.ui.BrowserController
import org.sableos.browser.ui.BrowserScreen
import org.sableos.browser.ui.BrowserState
import org.sableos.browser.ui.createBrowserWebView
import org.sableos.design.SableGlobalTheme

/**
 * Sable Browser: Android's WebView with a BB10-style trackpad pointer and keys. Opens web links
 * and web searches from other apps; Command's Search the web lands here when it is the default.
 */
class MainActivity : ComponentActivity() {
    private val state = BrowserState()
    private lateinit var webView: WebView
    private lateinit var controller: BrowserController
    private var pendingFiles: ValueCallback<Array<Uri>>? = null

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pendingFiles?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
            pendingFiles = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        webView =
            createBrowserWebView(this, state, onScale = { controller.pageScale = it }) { intent, callback ->
                chooseFiles(intent, callback)
            }
        controller = BrowserController(webView, state, Actions())
        onBackPressedDispatcher.addCallback(this, BackCallback())
        setContent {
            SableGlobalTheme(window = window) {
                BrowserScreen(state, webView, onGo = ::go, onLeaveAddress = ::leaveAddress)
            }
        }
        val restored = savedInstanceState?.let { webView.restoreState(it) } != null
        if (!restored) open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (!state.addressFocused && controller.onKey(event)) || super.dispatchKeyEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) controller.hidePointer()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // The trackpad in mouse mode draws Android's own pointer; ours steps aside.
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) controller.hidePointer()
        return super.dispatchGenericMotionEvent(event)
    }

    /** A web link, a web search, or nothing (the address bar takes focus). */
    private fun open(intent: Intent?) {
        val url =
            when (intent?.action) {
                Intent.ACTION_VIEW -> intent.dataString?.takeIf(UrlInput::isWeb)
                Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let(UrlInput::resolve)
                else -> null
            }
        if (url != null) {
            webView.loadUrl(url)
        } else if (webView.url == null) {
            webView.loadUrl(BLANK)
            state.addressRequest++
        }
    }

    private fun go(text: String) {
        val url = UrlInput.resolve(text) ?: return
        leaveAddress()
        if (UrlInput.isWeb(url)) {
            webView.loadUrl(url)
        } else {
            startOutside(url)
        }
    }

    private fun startOutside(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "no app for typed address", e)
            state.status = t("no_app_for_link")
        }
    }

    private fun leaveAddress() {
        state.addressFocused = false
        webView.requestFocus()
    }

    private fun chooseFiles(
        intent: Intent,
        callback: ValueCallback<Array<Uri>>,
    ): Boolean {
        pendingFiles?.onReceiveValue(null)
        pendingFiles = callback
        return try {
            pickFiles.launch(Intent.createChooser(intent, t("choose_file")))
            true
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "no file picker", e)
            pendingFiles = null
            false
        }
    }

    private inner class Actions : BrowserController.Actions {
        override fun share(url: String) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            startActivity(Intent.createChooser(send, t("share_page")))
        }

        override fun focusAddress() {
            state.addressRequest++
        }
    }

    /** Back leaves the address bar, then a page field, then goes back a page, then closes. */
    private inner class BackCallback : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                state.addressFocused -> leaveAddress()
                controller.back() -> Unit
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
    }

    private companion object {
        const val TAG = "SableBrowser"
        const val BLANK = "about:blank"
    }
}
