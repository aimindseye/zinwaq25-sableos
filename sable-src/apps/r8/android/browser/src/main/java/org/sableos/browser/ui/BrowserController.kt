package org.sableos.browser.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import org.sableos.browser.core.BrowserCommand
import org.sableos.browser.core.BrowserKeys
import org.sableos.browser.core.BrowserText.t
import org.sableos.browser.core.PointerPhysics

/**
 * Keys on the page: the trackpad pointer and the browser keys. With the pointer on, each trackpad
 * stroke (an arrow key) moves it by [PointerPhysics]; pushing past an edge scrolls; the click
 * presses where it points as a touch would, so holding it long-presses and moving while holding
 * drags. While a field on the page has focus, every key types into it until Esc or Back.
 */
class BrowserController(
    private val webView: WebView,
    private val state: BrowserState,
    private val actions: Actions,
) {
    /** What leaves the page: sharing, and asking the address bar for focus. */
    interface Actions {
        fun share(url: String)

        fun focusAddress()
    }

    private var streak: PointerPhysics.Streak? = null
    private var touchDownAt = 0L
    private var pressing = false

    /** CSS pixels per view pixel times the page zoom, from WebViewClient.onScaleChanged. */
    var pageScale = webView.resources.displayMetrics.density

    /** True while this class sends a touch, so the activity can tell it from the user's own. */
    var synthesizing = false
        private set

    /** Handles a key the address bar did not take; false lets the page have it. */
    fun onKey(event: KeyEvent): Boolean {
        val arrow = BrowserKeys.arrow(event.keyCode)
        return when {
            state.editing -> editingKey(event)
            arrow != null -> arrowKey(event, arrow)
            BrowserKeys.isClick(event.keyCode) && state.pointerOn && state.pointerShown -> clickKey(event)
            else -> commandKey(event)
        }
    }

    private fun arrowKey(
        event: KeyEvent,
        arrow: Pair<Int, Int>,
    ): Boolean {
        // With the pointer off, arrows go to the page, which scrolls.
        if (state.pointerOn && event.action == KeyEvent.ACTION_DOWN) move(arrow.first, arrow.second)
        return state.pointerOn
    }

    private fun clickKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            press()
        } else if (event.action == KeyEvent.ACTION_UP) {
            release()
        }
        return true
    }

    private fun commandKey(event: KeyEvent): Boolean {
        val other = event.metaState and OTHER_MODIFIERS != 0
        val command = BrowserKeys.command(event.keyCode, event.isShiftPressed, other) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) run(command)
        return true
    }

    /** Back: leave a page field, else go back a page; false when there is nowhere to go. */
    fun back(): Boolean =
        when {
            state.editing -> {
                leaveField()
                true
            }

            webView.canGoBack() -> {
                webView.goBack()
                true
            }

            else -> false
        }

    /** A real touch or mouse: the pointer hides until the next trackpad stroke. */
    fun hidePointer() {
        state.pointerShown = false
    }

    private fun editingKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        if (event.action == KeyEvent.ACTION_UP) leaveField()
        return true
    }

    private fun leaveField() {
        webView.evaluateJavascript(BLUR_JS, null)
        state.editing = false
    }

    private fun move(
        dx: Int,
        dy: Int,
    ) {
        if (!state.pointerShown) {
            // The first stroke shows the pointer in the middle of the page.
            state.pointerX = webView.width / 2f
            state.pointerY = webView.height / 2f
            state.pointerShown = true
            return
        }
        val next = PointerPhysics.nextStreak(streak, SystemClock.uptimeMillis())
        streak = next
        val step = PointerPhysics.stepDp(next, state.overLink) * webView.resources.displayMetrics.density
        val moved =
            PointerPhysics.move(
                state.pointerX,
                state.pointerY,
                dx * step,
                dy * step,
                webView.width.toFloat(),
                webView.height.toFloat(),
            )
        state.pointerX = moved.x
        state.pointerY = moved.y
        if (moved.scrollX != 0 || moved.scrollY != 0) webView.scrollBy(moved.scrollX, moved.scrollY)
        if (pressing) touch(MotionEvent.ACTION_MOVE)
        probeLink()
    }

    /** Asks the page whether a link, button or field is under the pointer (friction and the ring). */
    private fun probeLink() {
        val x = state.pointerX / pageScale
        val y = state.pointerY / pageScale
        webView.evaluateJavascript("($LINK_JS)($x,$y)") { state.overLink = it == "true" }
    }

    private fun press() {
        touchDownAt = SystemClock.uptimeMillis()
        pressing = true
        touch(MotionEvent.ACTION_DOWN)
    }

    private fun release() {
        if (!pressing) return
        pressing = false
        touch(MotionEvent.ACTION_UP)
        // A click on a field focuses it: then keys type into it.
        webView.postDelayed({ checkEditing() }, FIELD_CHECK_MS)
    }

    private fun checkEditing() {
        webView.evaluateJavascript(EDITING_JS) { state.editing = it == "true" }
    }

    private fun touch(action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(touchDownAt, now, action, state.pointerX, state.pointerY, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        synthesizing = true
        try {
            webView.dispatchTouchEvent(event)
        } finally {
            synthesizing = false
            event.recycle()
        }
    }

    private fun run(command: BrowserCommand) {
        when (command) {
            BrowserCommand.Address -> actions.focusAddress()
            BrowserCommand.PageDown -> webView.pageDown(false)
            BrowserCommand.PageUp -> webView.pageUp(false)
            BrowserCommand.Top -> webView.pageUp(true)
            BrowserCommand.Bottom -> webView.pageDown(true)
            BrowserCommand.Reload -> webView.reload()
            BrowserCommand.Back -> back()
            BrowserCommand.Forward -> if (webView.canGoForward()) webView.goForward()
            BrowserCommand.ZoomIn -> webView.zoomIn()
            BrowserCommand.ZoomOut -> webView.zoomOut()
            BrowserCommand.TogglePointer -> togglePointer()
            BrowserCommand.Share -> state.url?.let(actions::share)
        }
    }

    private fun togglePointer() {
        state.pointerOn = !state.pointerOn
        if (!state.pointerOn) state.pointerShown = false
        state.status = t(if (state.pointerOn) "pointer_on" else "pointer_off")
    }

    private companion object {
        const val FIELD_CHECK_MS = 200L
        const val OTHER_MODIFIERS = KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON

        const val LINK_JS =
            "function(x,y){var e=document.elementFromPoint(x,y);return !!(e&&e.closest(" +
                "'a[href],button,input,select,textarea,label,summary,[role=button],[role=link],[onclick]'))}"
        const val EDITING_JS =
            "(function(){var a=document.activeElement;return !!a&&(a.isContentEditable||" +
                "/^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName))})()"
        const val BLUR_JS = "document.activeElement&&document.activeElement.blur()"
    }
}
