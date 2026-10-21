package org.sableos.titan2.vault.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.core.content.FileProvider
import org.sableos.titan2.vault.core.ConfirmAction
import org.sableos.titan2.vault.core.Confirmation
import org.sableos.titan2.vault.core.Section
import org.sableos.titan2.vault.core.VaultAction
import org.sableos.titan2.vault.core.VaultItem
import org.sableos.titan2.vault.core.VaultKeys
import org.sableos.titan2.vault.core.VaultText

/**
 * Keyboard-first Secure Vault. Every key has a touch twin (rows and the Import / Export / Lock now buttons).
 * The window is FLAG_SECURE: no screenshots, and Recents shows no preview.
 */
class VaultActivity : Activity() {
    private lateinit var store: VaultStore
    private lateinit var body: LinearLayout
    private var section = Section.Photos
    private var selected = 0
    private var items: List<VaultItem> = emptyList()
    private var message: String? = null
    private var pending: Confirmation? = null
    private var pendingItem: VaultItem? = null
    private var pendingOriginals: List<Uri> = emptyList()
    private var exporting: VaultItem? = null
    private var privateProfile = false
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
        store = VaultStore(this)
        privateProfile = PrivateSpace.isPrivateProfile(this)
        section = Section.fromId(savedInstanceState?.getString(STATE_SECTION))
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(PAD), px(PAD), px(PAD), px(PAD))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isFocusable = false
            addView(body)
        })
        // Android 16 apps get Back through the dispatcher (predictive back), not as a key event:
        // Back cancels a pending confirmation, otherwise leaves.
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (pending != null) cancel() else finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SECTION, section.id)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---- keys ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = toKey(keyCode, event)
        val action = if (privateProfile) VaultKeys.action(key) else leaveOnly(key)
        return when {
            pending != null -> {
                when (VaultKeys.confirm(key)) {
                    ConfirmAction.Confirm -> confirm()
                    ConfirmAction.Cancel -> cancel()
                    null -> Unit
                }
                true
            }

            action != null -> {
                perform(action)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun leaveOnly(key: VaultKeys.Key): VaultAction? =
        if (key == VaultKeys.Key.Back) VaultAction.Leave else null

    private fun toKey(keyCode: Int, event: KeyEvent): VaultKeys.Key = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> VaultKeys.Key.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> VaultKeys.Key.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> VaultKeys.Key.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> VaultKeys.Key.Right
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> VaultKeys.Key.Enter
        KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL -> VaultKeys.Key.Delete
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> VaultKeys.Key.Back
        else -> letter(event)
    }

    /** Plain letters only: Alt+letter is a digit on the Q25 and Sym (Right Alt) is paging, so both are ignored. */
    private fun letter(event: KeyEvent): VaultKeys.Key {
        if (event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) return VaultKeys.Key.Other
        val c = event.getUnicodeChar(0)
        return if (c > 0 && Character.isLetter(c)) VaultKeys.Key.Letter(c.toChar()) else VaultKeys.Key.Other
    }

    private fun perform(action: VaultAction) {
        when (action) {
            VaultAction.Up -> move(-1)
            VaultAction.Down -> move(1)
            VaultAction.PrevSection -> show(section.next(-1))
            VaultAction.NextSection -> show(section.next(1))
            VaultAction.Photos -> show(Section.Photos)
            VaultAction.Docs -> show(Section.Docs)
            VaultAction.Recordings -> show(Section.Recordings)
            VaultAction.Open -> ask(Confirmation::open)
            VaultAction.Import -> startImport()
            VaultAction.Export -> ask(Confirmation::export)
            VaultAction.Delete -> ask(Confirmation::delete)
            VaultAction.Lock -> lockNow()
            VaultAction.Leave -> finish()
        }
    }

    private fun move(dir: Int) {
        if (items.isEmpty()) return
        selected = (selected + dir).coerceIn(0, items.lastIndex)
        render()
    }

    private fun show(s: Section) {
        section = s
        selected = 0
        message = null
        render()
    }

    private fun current(): VaultItem? = items.getOrNull(selected)

    // ---- confirmations: nothing leaves or is removed without one ----

    private fun ask(build: (VaultItem) -> Confirmation) {
        val item = current() ?: return
        pendingItem = item
        pending = build(item)
        render()
    }

    private fun cancel() {
        pending = null
        pendingItem = null
        pendingOriginals = emptyList()
        message = "Cancelled. Nothing changed."
        render()
    }

    private fun confirm() {
        val c = pending ?: return
        val item = pendingItem
        pending = null
        pendingItem = null
        when (c.kind) {
            Confirmation.Kind.Open -> item?.let { openItem(it) }
            Confirmation.Kind.Export -> item?.let { startExport(it) }
            Confirmation.Kind.Delete -> item?.let {
                val deleted = store.delete(section, it.name)
                message = if (deleted) "Deleted ${it.name}." else "Could not delete ${it.name}."
            }
            Confirmation.Kind.DeleteOriginals -> {
                val removed = store.deleteOriginals(pendingOriginals)
                message = "Deleted $removed of ${pendingOriginals.size} original(s). " +
                    "Any not deleted are still where they were."
                pendingOriginals = emptyList()
            }
        }
        render()
    }

    // ---- import / export / open ----

    private fun startImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, section.mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        launch(intent, REQUEST_IMPORT)
    }

    private fun startExport(item: VaultItem) {
        exporting = item
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeOf(item.name)
            putExtra(Intent.EXTRA_TITLE, item.name)
        }
        launch(intent, REQUEST_EXPORT)
    }

    private fun openItem(item: VaultItem) {
        val file = store.file(section, item.name) ?: return
        val uri = FileProvider.getUriForFile(this, AUTHORITY, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(item.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(view, "Open ${item.name} with"))
            message = "Opened ${item.name} (read-only, this file only)."
        } catch (_: ActivityNotFoundException) {
            message = "No app in Private Space can open ${item.name}."
        }
    }

    private fun launch(intent: Intent, request: Int) {
        try {
            startActivityForResult(intent, request)
        } catch (_: ActivityNotFoundException) {
            message = "The system file picker is not available."
            render()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) {
            message = "Cancelled. Nothing changed."
            exporting = null
            return
        }
        when (requestCode) {
            REQUEST_IMPORT -> finishImport(picked(data))
            REQUEST_EXPORT -> finishExport(data.data)
        }
    }

    private fun picked(data: Intent): List<Uri> {
        val clip = data.clipData
        return if (clip != null) {
            (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        } else {
            listOfNotNull(data.data)
        }
    }

    private fun finishImport(uris: List<Uri>) {
        val outcome = store.import(section, uris)
        message = outcome.message()
        if (outcome.imported.isNotEmpty()) {
            pendingOriginals = uris
            pending = Confirmation.deleteOriginals(outcome.imported.size)
        }
    }

    private fun finishExport(target: Uri?) {
        val item = exporting ?: return
        exporting = null
        message = if (target != null && store.export(section, item.name, target)) {
            "Exported a copy of ${item.name}. It is no longer protected by the vault."
        } else {
            "Export failed. Nothing was written."
        }
    }

    private fun lockNow() {
        pending = null
        if (!PrivateSpace.lockNow(this)) {
            Toast.makeText(this, VaultText.LOCK_FALLBACK, Toast.LENGTH_LONG).show()
        }
        finishAndRemoveTask()
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    // ---- rendering ----

    private fun render() {
        body.removeAllViews()
        body.addView(text(VaultText.TITLE, TITLE_SP, TEXT, bold = true))
        body.addView(text(VaultText.SUBTITLE, SMALL_SP, MUTED))
        if (!privateProfile) {
            body.addView(text(VaultText.NOT_PRIVATE, BODY_SP, WARN).apply { setPadding(0, px(GAP), 0, 0) })
            return
        }
        body.addView(tabs())
        items = store.list(section)
        selected = selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (items.isEmpty()) body.addView(text(VaultText.EMPTY, BODY_SP, MUTED))
        items.forEachIndexed { i, item -> body.addView(row(i, item)) }
        pending?.let { body.addView(confirmBox(it)) }
        message?.let { body.addView(text(it, SMALL_SP, if (isCaution(it)) WARN else OK)) }
        body.addView(buttons())
        body.addView(text(VaultKeys.HINT, SMALL_SP, MUTED).apply { setPadding(0, px(GAP), 0, 0) })
    }

    private fun isCaution(msg: String): Boolean =
        listOf("Could", "failed", "Cancelled", "No app", "not available").any { msg.contains(it) }

    private fun tabs(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, px(GAP), 0, px(GAP))
        Section.entries.forEach { s ->
            addView(text(s.title, BODY_SP, if (s == section) ACCENT else MUTED, bold = s == section).apply {
                setPadding(0, 0, px(GAP * 2), 0)
                setOnClickListener { show(s) }
            })
        }
    }

    private fun row(i: Int, item: VaultItem): View {
        val on = i == selected
        return text("${item.name}  ·  ${VaultText.size(item.sizeBytes)}", BODY_SP, TEXT).apply {
            setPadding(px(GAP), px(GAP), px(GAP), px(GAP))
            background = box(if (on) SURFACE else BG, if (on) ACCENT else BG)
            setOnClickListener {
                selected = i
                render()
            }
        }
    }

    private fun confirmBox(c: Confirmation): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(GAP), px(GAP), px(GAP), px(GAP))
        background = box(SURFACE, WARN)
        addView(text(c.title, BODY_SP, TEXT, bold = true))
        addView(text(c.detail, SMALL_SP, MUTED))
        addView(LinearLayout(this@VaultActivity).apply {
            addView(button("Yes") { confirm() })
            addView(button("No") { cancel() })
        })
    }

    private fun buttons(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, px(GAP), 0, 0)
        addView(button("Import") { perform(VaultAction.Import) })
        addView(button("Export") { perform(VaultAction.Export) })
        addView(button("Lock now") { perform(VaultAction.Lock) })
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        // Keys go to the vault list, not to a focused button (the trackpad sends arrow keys).
        isFocusable = false
        setOnClickListener { onClick() }
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
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

    private fun px(v: Int): Int = (v * dp).toInt()

    private companion object {
        const val AUTHORITY = "org.sableos.titan2.vault.files"
        const val STATE_SECTION = "section"
        const val REQUEST_IMPORT = 1
        const val REQUEST_EXPORT = 2
        const val PAD = 12
        const val GAP = 6
        const val STROKE = 2
        const val CORNER = 6f
        const val TITLE_SP = 18f
        const val BODY_SP = 14f
        const val SMALL_SP = 11f
        val BG = Color.rgb(0x06, 0x12, 0x1D)
        val SURFACE = Color.rgb(0x0C, 0x1E, 0x2D)
        val TEXT = Color.rgb(0xEE, 0xF8, 0xFF)
        val MUTED = Color.rgb(0x8B, 0xA6, 0xB8)
        val ACCENT = Color.rgb(0x15, 0x87, 0xFF)
        val WARN = Color.rgb(0xFF, 0xB7, 0x4D)
        val OK = Color.rgb(0x4C, 0xC3, 0x8A)
    }
}
