package org.sableos.messages

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.sableos.design.SableGlobalTheme
import org.sableos.messages.data.ProviderColumns
import org.sableos.messages.ui.MessagesApp

/**
 * Sable Messages: SMS threads from the telephony provider, sending with SmsManager and, as the
 * default SMS app, receiving SMS and MMS. Opens for sms:/smsto:/mms:/mmsto: links (Contacts'
 * "Message", Hub, Start) and from new-message notifications.
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: MessagesController
    private var observing = false

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                controller.scheduleReload()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = MessagesController(this)
        setContent {
            SableGlobalTheme(window = window) {
                MessagesApp(controller)
            }
        }
        if (savedInstanceState == null) controller.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller.handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Incoming texts (written by whichever app is the default) and our own sends both land here.
        contentResolver.registerContentObserver(ProviderColumns.MMS_SMS_ROOT, true, observer)
        observing = true
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        observing = false
        super.onStop()
    }

    companion object {
        const val ACTION_OPEN_THREAD = "org.sableos.messages.action.OPEN_THREAD"
        const val EXTRA_THREAD_ID = "thread_id"
    }
}
