package org.sableos.hub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.sableos.design.SableGlobalTheme
import org.sableos.hub.platform.AndroidSettingsLauncher
import org.sableos.hub.platform.DeviceAttention
import org.sableos.hub.policy.AndroidSettingsRoutes
import org.sableos.hub.policy.NotificationConcept
import org.sableos.hub.ui.AttentionSettingsScreen

/**
 * Settings > Notifications > Sable Attention ([org.sableos.hub.policy.HubIntents.ACTION_ATTENTION_SETTINGS]).
 *
 * Lists only the attention outputs this device profile validated. Android-delivered outputs link
 * to the owning Android Settings page instead of keeping a duplicate toggle.
 */
class AttentionSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = DeviceAttention.profile(applicationContext)
        val launcher = AndroidSettingsLauncher(this)

        setContent {
            SableGlobalTheme(window = window) {
                AttentionSettingsScreen(
                    profile = profile,
                    onOpenAndroid = { concept: NotificationConcept ->
                        launcher.open(AndroidSettingsRoutes.routeFor(concept))
                    },
                    onOpenConnectedApps = {
                        startActivity(Intent(this, ConnectedAppsActivity::class.java))
                    },
                    onBack = ::finish,
                )
            }
        }
    }
}
