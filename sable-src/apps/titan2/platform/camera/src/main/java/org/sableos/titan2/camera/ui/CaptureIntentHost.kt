package org.sableos.titan2.camera.ui

import android.app.Activity
import org.sableos.titan2.camera.android.CaptureResult
import org.sableos.titan2.camera.android.Saved
import org.sableos.titan2.camera.core.LaunchIntent
import org.sableos.titan2.camera.core.LaunchKind

/**
 * How the activity was launched. Started for IMAGE_CAPTURE, the result is cancelled until one
 * photo has been handed to the calling app; a normal launch keeps its captures in the session.
 */
class CaptureIntentHost(private val activity: Activity) {
    val launch = LaunchIntent.kind(activity.intent?.action)
    val forApp get() = launch == LaunchKind.ImageCapture
    private val result: CaptureResult? = if (forApp) CaptureResult(activity) else null

    init {
        if (forApp) activity.setResult(Activity.RESULT_CANCELED)
    }

    /**
     * IMAGE_CAPTURE: hand the JPEG to the calling app and close. [onReturning] runs first.
     * False when there is no caller to return to.
     */
    fun returnCapture(saved: Saved, onReturning: () -> Unit): Boolean {
        val r = result ?: return false
        onReturning()
        r.deliver(saved) { code, data ->
            activity.setResult(code, data)
            activity.finish()
        }
        return true
    }
}
