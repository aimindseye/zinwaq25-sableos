package org.sableos.titan2.camera.ui

import org.sableos.titan2.camera.core.ArrowGuard
import org.sableos.titan2.camera.core.Binding
import org.sableos.titan2.camera.core.CameraAction
import org.sableos.titan2.camera.core.CameraDeviceProfile
import org.sableos.titan2.camera.core.CameraKeyMap
import org.sableos.titan2.camera.core.ControlGate
import org.sableos.titan2.camera.core.DeckContext
import org.sableos.titan2.camera.core.HoldStep
import org.sableos.titan2.camera.core.HoldTracker
import org.sableos.titan2.camera.core.RawKeys

/**
 * Physical keys to actions. The quick review, when open, gets every key first. Keys with a hold
 * meaning (F, E) wait for release so a hold can become AF/AE lock; held keys otherwise repeat
 * only for stepping actions, while toggles and the shutter fire once per press. On a device whose
 * trackpad sends arrows, plain arrows are swallowed unless a parameter strip has the focus
 * ([ArrowGuard]); [onGuarded] then says to use Alt.
 */
class CameraKeys(
    private val profile: CameraDeviceProfile,
    private val review: QuickReviewHost,
    private val context: () -> DeckContext?,
    private val act: (CameraAction) -> Unit,
    private val stripFocused: () -> Boolean = { false },
    private val onGuarded: () -> Unit = {}
) {
    private val holds = HoldTracker()

    private val repeatable = setOf(
        CameraAction.StepUp,
        CameraAction.StepDown,
        CameraAction.ZoomIn,
        CameraAction.ZoomOut,
        CameraAction.ExposureUp,
        CameraAction.ExposureDown
    )

    /** True when the key was used; otherwise the activity passes it on (volume outside a profile). */
    fun down(keyCode: Int, alt: Boolean, repeat: Int): Boolean {
        val name = RawKeys.nameOf(keyCode)
        return when {
            name == null -> false

            review.isOpen -> review.key(name, alt, repeat)

            CameraKeyMap.hasHold(name, alt, profile) -> {
                holdDown(name, alt, repeat)
                true
            }

            else -> press(name, alt, repeat)
        }
    }

    /** A key with a hold meaning runs its ordinary press on release unless it was held. */
    fun up(keyCode: Int): Boolean {
        val tapped = RawKeys.nameOf(keyCode)?.takeIf { holds.up(it) }
        tapped?.let { CameraKeyMap.resolve(it, false, profile) }?.let(act)
        return tapped != null
    }

    /** Forget keys that are down, for example when the review opens over them. */
    fun clear() = holds.clear()

    private fun press(name: String, alt: Boolean, repeat: Int): Boolean {
        val action = CameraKeyMap.resolve(name, alt, profile)
        return when {
            ArrowGuard.blocks(Binding(name, alt), profile, stripFocused()) -> {
                if (repeat == 0) onGuarded()
                true
            }

            action == null -> false

            else -> {
                if (repeat == 0 || action in repeatable) act(action)
                true
            }
        }
    }

    private fun holdDown(name: String, alt: Boolean, repeat: Int) {
        val hold = CameraKeyMap.resolve(name, alt, profile, hold = true)
        if (holds.down(name, repeat) != HoldStep.Hold || hold == null) return
        val open = context()?.let { ControlGate.check(hold, it).isOpen } == true
        if (open) act(hold) else holds.holdRefused(name)
    }
}
