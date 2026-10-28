package org.sableos.titan2.profiles.android

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import org.sableos.titan2.profiles.core.SoundProfile

/** Quick Settings tile: shows the sound profile; tapping it lists the profiles to pick from. */
class SoundProfileTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val profiles = SoundProfile.entries
        val current = SoundProfileStore(this).current
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Sound profile")
            .setSingleChoiceItems(
                profiles.map {
                    it.title
                }.toTypedArray(),
                current?.ordinal ?: -1
            ) { d, which ->
                d.dismiss()
                pick(profiles[which])
            }
            .create()
        showDialog(dialog)
    }

    private fun pick(profile: SoundProfile) {
        when (SoundProfileApplier(this).apply(profile)) {
            SoundProfileApplier.Result.NEEDS_DND_ACCESS -> {
                Toast.makeText(
                    this,
                    "Allow Do Not Disturb access for Sound Profiles",
                    Toast.LENGTH_LONG
                ).show()
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            else -> refresh()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val current = SoundProfileStore(this).current
        tile.label = "Sound profile"
        tile.subtitle = current?.title ?: "Choose"
        tile.state =
            if (current == null ||
                current == SoundProfile.NORMAL
            ) {
                Tile.STATE_INACTIVE
            } else {
                Tile.STATE_ACTIVE
            }
        tile.updateTile()
    }
}
