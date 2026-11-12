package net.thunderbird.android

import android.app.Activity
import android.os.Bundle
import app.k9mail.legacy.message.controller.MessageReference
import com.fsck.k9.activity.MessageHomeActivity

/**
 * Opens one message from the SableOS Hub: org.sableos.mail.action.OPEN_MESSAGE with the row's
 * "reference". It only shows the user's own message in Sable Mail; anything else opens the inbox.
 */
class SableMailOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reference = MessageReference.parse(intent?.getStringExtra(EXTRA_REFERENCE))
        val target =
            if (reference != null) {
                MessageHomeActivity.actionDisplayMessageIntent(this, reference)
            } else {
                packageManager.getLaunchIntentForPackage(packageName)
            }
        target?.let { runCatching { startActivity(it) } }
        finish()
    }

    companion object {
        const val ACTION_OPEN_MESSAGE = "org.sableos.mail.action.OPEN_MESSAGE"
        const val EXTRA_REFERENCE = "reference"
    }
}
