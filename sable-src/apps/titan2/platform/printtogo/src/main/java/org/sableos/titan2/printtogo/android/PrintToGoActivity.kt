package org.sableos.titan2.printtogo.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import org.sableos.titan2.printtogo.android.PrintToGoState.Phase
import org.sableos.titan2.printtogo.core.PrintToGo
import org.sableos.titan2.printtogo.core.PrintToGoText.t
import org.sableos.titan2.theme.SablePalette

/**
 * Sable Print To Go's one screen: what it does, whether it is on, Start or Stop, and the folder prints are
 * saved in. Enter starts or stops, O opens the folder, Back leaves (Print To Go keeps running).
 */
class PrintToGoActivity : Activity() {
    private lateinit var palette: SablePalette
    private lateinit var status: TextView
    private lateinit var last: TextView
    private lateinit var toggle: Button
    private val listener: (PrintToGoState.Snapshot) -> Unit = ::show

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = SablePalette.read(this).also { it.applyTo(this) }
        val dp = resources.displayMetrics.density
        val pad = (PAD * dp).toInt()
        val body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
            }
        body.addView(
            text(
                getString(org.sableos.titan2.printtogo.R.string.app_name),
                TITLE_SP,
                palette.text,
                bold = true
            )
        )
        body.addView(text(t("intro"), BODY_SP, palette.muted))
        status = text("", BODY_SP, palette.text, bold = true).also(body::addView)
        last = text("", BODY_SP, palette.muted).also(body::addView)
        toggle = button("") { toggle() }.also(body::addView)
        body.addView(button(t("open_folder")) { openFolder() })
        body.addView(text(t("hint"), SMALL_SP, palette.muted))
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(palette.window)
                addView(body)
            }
        )
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        PrintToGoState.listen(listener)
    }

    override fun onStop() {
        PrintToGoState.forget(listener)
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER
            -> toggle()

            KeyEvent.KEYCODE_O -> openFolder()

            KeyEvent.KEYCODE_ESCAPE -> finish()

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun show(state: PrintToGoState.Snapshot) {
        val on = state.phase == Phase.Ready
        status.text =
            when (state.phase) {
                Phase.Ready -> t("ready", state.printerName)
                Phase.NoWifi -> t("no_wifi")
                Phase.Failed -> t("start_failed")
                Phase.IdleOff -> t("idle_off")
                Phase.Off -> t("off")
            }
        status.setTextColor(if (on) palette.ok else palette.text)
        last.text = state.last.orEmpty()
        toggle.text = if (on) t("stop") else t("start")
    }

    private fun toggle() {
        if (PrintToGoState.snapshot.phase == Phase.Ready) {
            startService(
                Intent(this, PrintToGoService::class.java).setAction(PrintToGoService.ACTION_STOP)
            )
        } else {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
            startForegroundService(Intent(this, PrintToGoService::class.java))
        }
    }

    /** The Files app on Documents/Print To Go. */
    private fun openFolder() {
        val folder =
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "primary:" + PrintToGo.FOLDER)
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (GAP * resources.displayMetrics.density).toInt(), 0, 0)
        }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        isFocusable = false
        setOnClickListener { onClick() }
    }

    private companion object {
        const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        const val PAD = 12
        const val GAP = 8
        const val TITLE_SP = 18f
        const val BODY_SP = 14f
        const val SMALL_SP = 11f
    }
}
