package org.sableos.phone.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.sableos.phone.core.DialKeys
import org.sableos.phone.core.PhoneText.t
import org.sableos.phone.core.SpeedDial
import org.sableos.titan2.theme.SablePalette

/**
 * The speed dial list, A to Z. Enter on a letter calls it, changes it or removes it; a letter with nothing on it
 * picks a contact. Pressing a letter key moves to that letter.
 */
class SpeedDialActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var store: SpeedDialStore
    private lateinit var list: LinearLayout
    private val rows = mutableMapOf<Char, TextView>()
    private var picking: Char? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = SablePalette.read(this).also { it.applyTo(this) }
        ui = Ui(this, palette)
        store = SpeedDialStore(this)
        picking = savedInstanceState?.getString(STATE_PICKING)?.singleOrNull()
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(Ui.PAD), ui.dp(Ui.PAD), ui.dp(Ui.PAD), ui.dp(Ui.PAD))
        }
        list.addView(ui.text(Ui.TITLE_SP, bold = true, s = t("speed_dial")))
        list.addView(ui.text(Ui.SMALL_SP, palette.muted, s = t("speed_dial_hint")))
        for (letter in 'a'..'z') {
            val row = ui.text(Ui.BODY_SP).apply {
                isFocusable = true
                isClickable = true
                background = ui.focusBackground()
                setPadding(ui.dp(Ui.PAD), ui.dp(ROW_PAD), ui.dp(Ui.PAD), ui.dp(ROW_PAD))
                setOnClickListener { choose(letter) }
            }
            rows[letter] = row
            list.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(list) })
        show()
        val assign = intent.getStringExtra(EXTRA_ASSIGN)?.singleOrNull()
        if (savedInstanceState == null && assign != null) {
            rows[assign]?.requestFocus()
            Toast.makeText(
                this,
                t("speed_dial_unassigned", assign.uppercaseChar()),
                Toast.LENGTH_LONG
            ).show()
            pick(assign)
        } else {
            rows['a']?.requestFocus()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        picking?.let { outState.putString(STATE_PICKING, it.toString()) }
    }

    private fun show() {
        val all = store.all()
        rows.forEach { (letter, row) ->
            val e = all[letter]
            row.text = if (e == null) {
                t("speed_dial_row_empty", letter.uppercaseChar())
            } else {
                t("speed_dial_row", letter.uppercaseChar(), e.label)
            }
        }
    }

    private fun choose(letter: Char) {
        val entry = store.get(letter) ?: return pick(letter)
        AlertDialog.Builder(this)
            .setTitle(t("speed_dial_row", letter.uppercaseChar(), entry.label))
            .setItems(arrayOf(t("call"), t("speed_dial_change"), t("speed_dial_remove"))) {
                    _,
                    which
                ->
                when (which) {
                    0 -> Dialing.call(this, entry.number)

                    1 -> pick(letter)

                    else -> {
                        store.remove(letter)
                        show()
                    }
                }
            }
            .show()
    }

    private fun pick(letter: Char) {
        picking = letter
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI), REQUEST_PICK)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val letter = picking
        val uri = data?.data?.takeIf { requestCode == REQUEST_PICK && resultCode == RESULT_OK }
        if (letter == null || uri == null) return
        val picked = runCatching {
            contentResolver.query(
                uri,
                arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME_PRIMARY),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null
            }
        }.getOrNull()
        if (picked == null || picked.first.isBlank()) {
            Toast.makeText(this, t("speed_dial_no_number"), Toast.LENGTH_LONG).show()
            return
        }
        val entry = SpeedDial.Entry(picked.first, picked.second.ifBlank { picked.first })
        store.put(letter, entry)
        show()
        Toast.makeText(
            this,
            t("speed_dial_assigned", letter.uppercaseChar(), entry.label),
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val letter = DialKeys.letterFor(keyCode)
        if (letter != null && event.metaState and DialKeys.CHORD_META == 0 &&
            event.repeatCount == 0
        ) {
            rows[letter]?.requestFocus()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val EXTRA_ASSIGN = "org.sableos.phone.extra.ASSIGN"
        private const val STATE_PICKING = "picking"
        private const val REQUEST_PICK = 1
        private const val ROW_PAD = 10

        /** Opens the list and goes straight to choosing a contact for [letter]. */
        fun assign(context: Context, letter: Char): Intent =
            Intent(context, SpeedDialActivity::class.java)
                .putExtra(EXTRA_ASSIGN, letter.lowercaseChar().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
