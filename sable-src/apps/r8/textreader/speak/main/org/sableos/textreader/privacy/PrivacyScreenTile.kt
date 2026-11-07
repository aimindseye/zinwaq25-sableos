package org.sableos.textreader.privacy

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.vaachak.textreader.R

/**
 * The Privacy Screen Quick Settings tile. A tap switches the Privacy Screen on or off; while its
 * accessibility service is not turned on, the tap opens that service's page in Accessibility
 * settings instead, so the user turns it on there (Android does not let an app do it itself).
 */
class PrivacyScreenTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (!PrivacyScreenService.toggle()) openAccessibilityPage()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        // Set here, not in the manifest, so the app's launcher icon stays the only android:icon there.
        tile.icon = Icon.createWithResource(this, R.drawable.ic_sable_privacy_screen)
        tile.state =
            when {
                !PrivacyScreenService.available -> Tile.STATE_INACTIVE
                PrivacyScreenService.on -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
        tile.updateTile()
    }

    // Below Android 14 only the Intent form exists; the PendingIntent form is used from 14 up.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAccessibilityPage() {
        val intent =
            Intent(ACTION_ACCESSIBILITY_DETAILS)
                .putExtra(
                    EXTRA_COMPONENT_NAME,
                    ComponentName(this, PrivacyScreenService::class.java).flattenToString(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, flags))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

        /** Settings' own extra naming the accessibility service to open (Intent.EXTRA_COMPONENT_NAME). */
        const val EXTRA_COMPONENT_NAME = "android.intent.extra.COMPONENT_NAME"
    }
}
