package org.sableos.titan2.profiles.android

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.sableos.titan2.profiles.core.ProfileSettings
import org.sableos.titan2.profiles.core.SoundProfile
import org.sableos.titan2.profiles.core.SoundProfiles

/**
 * BlackBerry sound profiles: Use picks one, Edit sets that profile's tones, volume, vibration and
 * light. Plain views, so the keyboard and trackpad reach everything. Also the Quick Settings tile's
 * long-press screen.
 */
class SoundProfilesActivity : Activity() {
    private lateinit var store: SoundProfileStore
    private lateinit var status: TextView
    private lateinit var editor: LinearLayout
    private val rows = mutableMapOf<SoundProfile, Button>()
    private var editing = SoundProfile.NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SoundProfileStore(this)
        editing = store.current ?: SoundProfile.NORMAL
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PAD, PAD, PAD, PAD)
        }
        col.addView(text("Sound profiles", TITLE_SP))
        status = text("", NOTE_SP)
        col.addView(status)
        SoundProfile.entries.forEach { profile ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val use = Button(this).apply { setOnClickListener { use(profile) } }
            rows[profile] = use
            row.addView(use, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(
                Button(this).apply {
                    text = "Edit"
                    setOnClickListener {
                        editing = profile
                        refresh()
                    }
                }
            )
            col.addView(row)
        }
        editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(editor)
        col.addView(
            text(
                "Add the Sound profile tile to Quick Settings to switch from the shade: pull " +
                    "down the shade, choose Edit and drag the tile in.",
                NOTE_SP
            )
        )
        setContentView(
            ScrollView(this).apply {
                addView(
                    col,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun use(profile: SoundProfile) {
        status.text = when (SoundProfileApplier(this).apply(profile)) {
            SoundProfileApplier.Result.APPLIED -> "${profile.title} is on."

            SoundProfileApplier.Result.PARTIAL -> {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                )
                "${profile.title} is on. Allow changing system settings so it can set tones, " +
                    "vibration and the light, then choose it again."
            }

            SoundProfileApplier.Result.NEEDS_DND_ACCESS -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                "Allow Do Not Disturb access for Sound Profiles, then choose ${profile.title} again."
            }
        }
        refresh()
    }

    private fun refresh() {
        val current = store.current
        rows.forEach { (profile, button) ->
            val mark = if (profile == current) "● " else "○ "
            button.text = "$mark${profile.title}\n${profile.summary}"
        }
        buildEditor()
    }

    private fun buildEditor() {
        editor.removeAllViews()
        val profile = editing
        val s = store.settings(profile)
        val plan = SoundProfiles.plan(profile, s)
        editor.addView(text("Edit ${profile.title}", TITLE_SP))
        if (plan.volumePercent != null) {
            val volume = plan.volumePercent
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                text("Volume $volume%", NOTE_SP),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(
                button("−") {
                    save(profile, s.copy(volumePercent = (volume - STEP).coerceAtLeast(0)))
                }
            )
            row.addView(
                button("+") {
                    save(profile, s.copy(volumePercent = (volume + STEP).coerceAtMost(FULL)))
                }
            )
            editor.addView(row)
        }
        editor.addView(
            button("Ringtone: ${toneName(s.ringtone)}") {
                pickTone(RingtoneManager.TYPE_RINGTONE, s.ringtone)
            }
        )
        editor.addView(
            button("Notification tone: ${toneName(s.notificationTone)}") {
                pickTone(RingtoneManager.TYPE_NOTIFICATION, s.notificationTone)
            }
        )
        editor.addView(
            switch("Vibrate for calls", plan.vibrateWhenRinging) {
                save(profile, s.copy(vibrate = it))
            }
        )
        editor.addView(
            switch("Notification light", plan.light) {
                save(profile, s.copy(light = it))
            }
        )
    }

    private fun save(profile: SoundProfile, settings: ProfileSettings) {
        store.save(profile, settings)
        if (store.current == profile) SoundProfileApplier(this).apply(profile)
        refresh()
    }

    // startActivityForResult: a plain Activity, no AndroidX result API here.
    @Suppress("DEPRECATION")
    private fun pickTone(type: Int, current: String?) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let(Uri::parse))
        startActivityForResult(intent, type)
    }

    @Deprecated("Activity result API of a plain Activity")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.getParcelableExtra(
            RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
            Uri::class.java
        )
        if (resultCode != RESULT_OK || uri == null) return
        val s = store.settings(editing)
        save(
            editing,
            if (requestCode == RingtoneManager.TYPE_RINGTONE) {
                s.copy(ringtone = uri.toString())
            } else {
                s.copy(notificationTone = uri.toString())
            }
        )
    }

    private fun toneName(uri: String?): String = uri?.let {
        runCatching { RingtoneManager.getRingtone(this, Uri.parse(it))?.getTitle(this) }.getOrNull()
    } ?: "phone's current"

    private fun text(s: String, size: Float) = TextView(this).apply {
        text = s
        textSize = size
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun switch(label: String, on: Boolean, onChange: (Boolean) -> Unit) =
        Switch(this).apply {
            text = label
            isChecked = on
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }

    private companion object {
        const val PAD = 24
        const val TITLE_SP = 20f
        const val NOTE_SP = 14f
        const val STEP = 10
        const val FULL = 100
    }
}
