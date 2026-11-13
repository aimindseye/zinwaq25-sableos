package org.sableos.textreader.privacy

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

/**
 * Privacy Screen (Sable 2.0): dims the whole screen except a band around the item that has focus,
 * so people beside you cannot read it. The band follows focus as the trackpad or keys move it.
 *
 * Off by default. Turning on this accessibility service only makes the Privacy Screen available;
 * the Quick Settings tile (or My Sable) switches it on and off, and the choice is kept in memory
 * only. It never shows on the lock screen, with the screen off, or over a call screen. It reads
 * only the bounds of the focused item, never its text, and it takes no touch or keys.
 */
class PrivacyScreenService : AccessibilityService() {
    private var overlay: Shade? = null
    private var frontPackage: String? = null
    private var screenOn = true

    private val screenEvents =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                screenOn = intent.action != Intent.ACTION_SCREEN_OFF
                apply()
            }
        }

    override fun onServiceConnected() {
        instance = WeakReference(this)
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenEvents, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenEvents, filter)
        }
        requestTileUpdate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            frontPackage = event.packageName?.toString()
        }
        if (!on) return
        apply()
        val node =
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                -> event.source

                else -> rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }
        val bounds = node?.let { Rect().also(it::getBoundsInScreen) }
        overlay?.moveTo(bounds)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenEvents) }
        hide()
        instance = null
        on = false
        requestTileUpdate()
        super.onDestroy()
    }

    /** Shows or hides the shade for the current state. */
    private fun apply() {
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: true
        if (PrivacyBand.mayShow(on, locked, screenOn, frontPackage)) show() else hide()
    }

    private fun show() {
        if (overlay != null) return
        val shade = Shade(this)
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
                }
        runCatching { getSystemService(WindowManager::class.java).addView(shade, params) }
            .onSuccess { overlay = shade }
            .onFailure { Log.w(TAG, "could not show the shade", it) }
    }

    private fun hide() {
        val shade = overlay ?: return
        overlay = null
        runCatching { getSystemService(WindowManager::class.java).removeView(shade) }
    }

    private fun setOn(value: Boolean) {
        on = value
        apply()
        if (value) {
            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val bounds = focused?.let { Rect().also(it::getBoundsInScreen) }
            overlay?.moveTo(bounds)
            Log.i(TAG, "on; shade shown=${overlay != null}, front=$frontPackage, focus=$bounds")
        } else {
            Log.i(TAG, "off")
        }
        requestTileUpdate()
    }

    private fun requestTileUpdate() {
        runCatching { TileService.requestListeningState(this, ComponentName(this, PrivacyScreenTile::class.java)) }
    }

    /** The dark layer: everything but the band, drawn black at [SHADE_ALPHA]. */
    private class Shade(
        context: Context,
    ) : View(context) {
        private val paint = Paint().apply { color = Color.argb(SHADE_ALPHA, 0, 0, 0) }
        private val minBand = (MIN_BAND_DP * context.resources.displayMetrics.density).toInt()
        private val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
        private var focus: Rect? = null

        fun moveTo(bounds: Rect?) {
            focus = bounds?.takeIf { !it.isEmpty }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val location = IntArray(2).also(::getLocationOnScreen)
            val focusTop = focus?.top?.minus(location[1])
            val focusBottom = focus?.bottom?.minus(location[1])
            val band = PrivacyBand.bandFor(focusTop, focusBottom, height, minBand, padding)
            canvas.drawRect(0f, 0f, width.toFloat(), band.top.toFloat(), paint)
            canvas.drawRect(0f, band.bottom.toFloat(), width.toFloat(), height.toFloat(), paint)
        }
    }

    companion object {
        private const val TAG = "SablePrivacy"
        private const val SHADE_ALPHA = 240
        private const val MIN_BAND_DP = 96
        private const val PADDING_DP = 12
        private var instance: WeakReference<PrivacyScreenService>? = null

        /** Whether the Privacy Screen is switched on (kept in memory only; off after a restart). */
        @Volatile
        var on: Boolean = false
            private set

        /** Whether the accessibility service is running, so the tile can switch the screen. */
        val available: Boolean
            get() = instance?.get() != null

        /** Switches the Privacy Screen; false when the service is not turned on in Accessibility. */
        fun toggle(): Boolean {
            val service = instance?.get() ?: return false
            service.setOn(!on)
            return true
        }
    }
}
