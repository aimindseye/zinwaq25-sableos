package org.sableos.titan2.profiles.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.sableos.titan2.profiles.core.ProfileSettings
import org.sableos.titan2.profiles.core.ProfilesText.t
import org.sableos.titan2.profiles.core.SoundProfile
import org.sableos.titan2.profiles.core.SoundProfiles
import org.sableos.titan2.theme.SablePalette

/**
 * BlackBerry sound profiles in the Sable look of the other platform apps, laid out as the
 * BlackBerry 7 UI guidelines ask: a vertical list, the first row focused, a high-contrast highlight
 * (accent fill, same font), check marks for on/off settings that Space or Enter toggles, a full
 * menu on the Menu key with every action, and shortcut keys for the common ones (E edits, T/B go
 * to the top/bottom). Also the Quick Settings tile's long-press screen.
 */
class SoundProfilesActivity : Activity() {
    private lateinit var store: SoundProfileStore
    private lateinit var body: LinearLayout
    private var palette = SablePalette.of(dark = true, accent = null)
    private var editing = SoundProfile.NORMAL
    private var status: String? = null
    private var focusKey: String? = null
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = SablePalette.read(this).also { it.applyTo(this) }
        store = SoundProfileStore(this)
        editing = store.current ?: SoundProfile.NORMAL
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(PAD), px(PAD), px(PAD), px(PAD))
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(palette.window)
                isFocusable = false
                addView(body)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val plain = !event.isAltPressed && !event.isCtrlPressed
        val profile = focusedProfile()
        val handled = plain && when (keyCode) {
            KeyEvent.KEYCODE_MENU -> true.also { showMenu() }
            KeyEvent.KEYCODE_E -> profile != null && true.also { edit(profile) }
            KeyEvent.KEYCODE_T -> true.also { focusRow(first = true) }
            KeyEvent.KEYCODE_B -> true.also { focusRow(first = false) }
            else -> false
        }
        return handled || super.onKeyDown(keyCode, event)
    }

    private fun focusedProfile(): SoundProfile? =
        (currentFocus?.tag as? String)?.let(SoundProfile::fromName)

    private fun focusRow(first: Boolean) {
        val rows = (0 until body.childCount).map(body::getChildAt).filter { it.isFocusable }
        (if (first) rows.firstOrNull() else rows.lastOrNull())?.requestFocus()
    }

    /** The full menu: the focused profile's actions first, then the screen's, Close last. */
    private fun showMenu() {
        val profile = focusedProfile() ?: editing
        val items = listOf(
            t("use", profile.title) to { use(profile) },
            t("edit", profile.title) to { edit(profile) },
            t("quick_settings_tile") to {
                status = t("pull_down_the_shade_choose")
                render()
            },
            t("close") to { finish() }
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun render() {
        focusKey = (currentFocus?.tag as? String) ?: focusKey
        body.removeAllViews()
        body.addView(text(t("sound_profiles"), TITLE_SP, palette.text, bold = true))
        body.addView(
            text(t("enter_uses_a_profile_e"), SMALL_SP, palette.muted)
        )
        status?.let { body.addView(text(it, SMALL_SP, palette.warn).apply { pad(0, GAP, 0, 0) }) }
        body.addView(gap())
        val current = store.current
        SoundProfile.entries.forEach { profile ->
            body.addView(
                row(
                    key = profile.name,
                    title = (if (profile == current) "● " else "○ ") + profile.title,
                    detail = profile.summary,
                    on = profile == current,
                    onClick = { use(profile) }
                ).onLong { edit(profile) }
            )
        }
        body.addView(gap())
        editor()
        body.addView(
            text(
                t("quick_settings_pull_down_the"),
                SMALL_SP,
                palette.muted
            ).apply { pad(0, GAP * 2, 0, 0) }
        )
        // The last focused row again, else the first profile (BlackBerry 7: one item always has focus).
        (
            focusKey?.let { body.findViewWithTag<View>(it) }
                ?: body.findViewWithTag(SoundProfile.entries.first().name)
            )
            ?.requestFocus()
    }

    private fun editor() {
        val profile = editing
        val s = store.settings(profile)
        val plan = SoundProfiles.plan(profile, s)
        body.addView(text(t("edit", profile.title), BODY_SP, palette.accent, bold = true))
        plan.volumePercent?.let { volume ->
            body.addView(
                row(
                    key = "volume",
                    title = t("volume", volume),
                    detail = t("enter_adds_10_shift_enter"),
                    onClick = { step(profile, s, volume, +STEP) }
                ).onLong { step(profile, s, volume, -STEP) }.apply {
                    setOnKeyListener { _, code, ev ->
                        val down = code == KeyEvent.KEYCODE_ENTER && ev.isShiftPressed
                        if (down && ev.action == KeyEvent.ACTION_UP) step(profile, s, volume, -STEP)
                        down
                    }
                }
            )
        }
        body.addView(
            row("ringtone", t("ringtone"), toneName(s.ringtone), onClick = {
                pickTone(RingtoneManager.TYPE_RINGTONE, s.ringtone)
            })
        )
        body.addView(
            row("notification", t("notification_tone"), toneName(s.notificationTone), onClick = {
                pickTone(RingtoneManager.TYPE_NOTIFICATION, s.notificationTone)
            })
        )
        body.addView(
            row(
                "vibrate",
                check(
                    plan.vibrateWhenRinging,
                    t("vibrate_for_calls")
                ),
                t("space_turns_it_on_or"),
                onClick = {
                    save(profile, s.copy(vibrate = !plan.vibrateWhenRinging))
                }
            )
        )
        body.addView(
            row(
                "light",
                check(
                    plan.light,
                    t("notification_light")
                ),
                t("space_turns_it_on_or"),
                onClick = {
                    save(profile, s.copy(light = !plan.light))
                }
            )
        )
    }

    private fun use(profile: SoundProfile) {
        editing = profile
        status = when (SoundProfileApplier(this).apply(profile)) {
            SoundProfileApplier.Result.APPLIED -> null

            SoundProfileApplier.Result.PARTIAL -> {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                )
                t("is_on_allow_changing_system", profile.title)
            }

            SoundProfileApplier.Result.NEEDS_DND_ACCESS -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                t("allow_do_not_disturb_access_2", profile.title)
            }
        }
        render()
    }

    private fun edit(profile: SoundProfile) {
        editing = profile
        focusKey = "volume"
        render()
        if (body.findViewWithTag<View>("volume") == null) {
            body.findViewWithTag<View>("ringtone")?.requestFocus()
        }
    }

    private fun step(profile: SoundProfile, s: ProfileSettings, volume: Int, by: Int) {
        save(profile, s.copy(volumePercent = (volume + by).coerceIn(0, FULL)))
    }

    private fun save(profile: SoundProfile, settings: ProfileSettings) {
        store.save(profile, settings)
        if (store.current == profile) SoundProfileApplier(this).apply(profile)
        render()
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
    } ?: t("phone_s_current_tone")

    private fun check(on: Boolean, label: String) = (if (on) "☑ " else "☐ ") + label

    /** A focusable two-line row: accent border when focused, accent text when it is the active one. */
    private fun row(
        key: String,
        title: String,
        detail: String,
        on: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        tag = key
        isFocusable = true
        isClickable = true
        pad(GAP * 2, GAP, GAP * 2, GAP)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(GAP / 2) }
        val titleView = text(title, BODY_SP, palette.text, bold = on)
        val detailView = text(detail, SMALL_SP, palette.muted)
        addView(titleView)
        addView(detailView)
        // Focus: accent fill and its text colour, the same font (BlackBerry 7 highlight).
        val paint = { has: Boolean ->
            background =
                box(
                    if (has) palette.accent else palette.surface,
                    if (on) palette.accent else palette.surface
                )
            titleView.setTextColor(
                if (has) {
                    palette.onAccent
                } else if (on) {
                    palette.accent
                } else {
                    palette.text
                }
            )
            detailView.setTextColor(if (has) palette.onAccent else palette.muted)
        }
        paint(false)
        setOnFocusChangeListener { _, has -> paint(has) }
        setOnKeyListener { _, code, ev ->
            val space = code == KeyEvent.KEYCODE_SPACE && ev.action == KeyEvent.ACTION_UP
            if (space) onClick()
            code == KeyEvent.KEYCODE_SPACE
        }
        setOnClickListener { onClick() }
    }

    private fun LinearLayout.onLong(action: () -> Unit) = apply {
        setOnLongClickListener {
            action()
            true
        }
    }

    private fun gap() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, px(GAP))
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun box(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = CORNER * dp
        setStroke(px(STROKE), stroke)
    }

    private fun View.pad(l: Int, t: Int, r: Int, b: Int) = setPadding(px(l), px(t), px(r), px(b))

    private fun px(v: Int): Int = (v * dp).toInt()

    private companion object {
        const val PAD = 12
        const val GAP = 6
        const val STROKE = 2
        const val CORNER = 8f
        const val TITLE_SP = 18f
        const val BODY_SP = 14f
        const val SMALL_SP = 11f
        const val STEP = 10
        const val FULL = 100
    }
}
