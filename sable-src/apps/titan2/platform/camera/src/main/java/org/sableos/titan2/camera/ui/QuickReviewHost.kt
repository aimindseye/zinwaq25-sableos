package org.sableos.titan2.camera.ui

import android.net.Uri
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.titan2.camera.android.ReviewActions
import org.sableos.titan2.camera.android.Saved
import org.sableos.titan2.camera.core.CameraText.t
import org.sableos.titan2.camera.core.Capture
import org.sableos.titan2.camera.core.QuickReview
import org.sableos.titan2.camera.core.ReviewAction
import org.sableos.titan2.camera.core.ReviewEffect
import org.sableos.titan2.camera.core.ReviewKeyMap
import org.sableos.titan2.camera.core.ReviewState
import org.sableos.titan2.camera.core.ReviewStep

/**
 * Glue between the pure [QuickReview] model and the platform: holds this session's captures,
 * answers review keys, and runs share/Files/open/delete only after an explicit press.
 */
class QuickReviewHost(private val actions: ReviewActions, private val onStatus: (String) -> Unit) {
    var state by mutableStateOf(ReviewState<Uri>())
        private set

    /** Back (gesture or key) closes the review, or cancels its delete question, first. */
    val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = act(ReviewAction.Close)
    }

    val isOpen: Boolean get() = state.open

    /** Keep a saved file for G and the thumbnail. Only this session's saves are listed. */
    fun remember(saved: Saved) = set(
        QuickReview.added(state, Capture(saved.uri, saved.name, saved.mime))
    )

    fun open() = apply(QuickReview.open(state))

    fun act(a: ReviewAction) = apply(QuickReview.apply(state, a))

    /** Review keys; Left/Right may repeat to flick through, nothing else does. */
    fun key(name: String, alt: Boolean, repeat: Int): Boolean {
        val a = ReviewKeyMap.resolve(name, alt, state.confirmingDelete) ?: return false
        if (repeat == 0 || a == ReviewAction.Older || a == ReviewAction.Newer) act(a)
        return true
    }

    suspend fun load(c: Capture<Uri>, edgePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        actions.thumbnail(c, edgePx)?.asImageBitmap()
    }

    private fun set(s: ReviewState<Uri>) {
        state = s
        back.isEnabled = s.open
    }

    private fun apply(step: ReviewStep<Uri>) {
        set(step.state)
        step.status?.let(onStatus)
        step.effect?.let(::run)
    }

    private fun run(e: ReviewEffect<Uri>) {
        when (e) {
            is ReviewEffect.Delete -> {
                val c = e.capture
                if (actions.delete(c)) {
                    set(QuickReview.removed(state, c.ref))
                    onStatus(t("deleted", c.name))
                } else {
                    onStatus(t("could_not_delete", c.name))
                }
            }

            is ReviewEffect.Share ->
                if (!actions.share(e.capture)) onStatus(t("no_app_can_share_this"))

            is ReviewEffect.ShowInFiles -> onStatus(
                if (actions.showInFiles()) {
                    t("files", ReviewActions.CAPTURE_FOLDER)
                } else {
                    t("files_is_not_available")
                }
            )

            is ReviewEffect.Open ->
                if (!actions.open(e.capture)) onStatus(t("no_app_can_open_this"))
        }
    }
}
