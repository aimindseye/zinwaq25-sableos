package org.sableos.titan2.spaces.android

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import org.sableos.titan2.spaces.android.SpacesViews.Companion.BODY_SP
import org.sableos.titan2.spaces.android.SpacesViews.Companion.PAD
import org.sableos.titan2.spaces.android.SpacesViews.Companion.TITLE_SP
import org.sableos.titan2.spaces.core.SpacesText.t
import org.sableos.titan2.spaces.core.WorkSpace
import org.sableos.titan2.theme.SablePalette

/**
 * Inside the Work space: adds an app the user already has, or removes the Work space. Any app of the
 * main user can send these two requests through the forwarding, so each is confirmed here first:
 * Enter adds the named package, and only Y removes the space.
 */
class InWorkActivity : Activity() {
    private sealed interface Request {
        data class Add(val packageName: String, val label: String) : Request
        data object Remove : Request
    }

    private var request: Request? = null
    private lateinit var dpm: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(DevicePolicyManager::class.java)
        request = if (dpm.isProfileOwnerApp(packageName)) read() else null
        val asked = request
        if (asked == null) {
            finish()
            return
        }
        val palette = SablePalette.read(this).also { it.applyTo(this) }
        val views = SpacesViews(this, palette)
        val line = when (asked) {
            is Request.Add -> t("add_confirm", asked.label, asked.packageName)
            Request.Remove -> t("remove_confirm")
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(views.px(PAD), views.px(PAD), views.px(PAD), views.px(PAD))
                addView(views.text(t("work_space"), TITLE_SP, palette.text, bold = true))
                addView(views.text(line, BODY_SP, palette.text))
                addView(
                    LinearLayout(this@InWorkActivity).apply {
                        addView(views.button(t("yes")) { confirm() })
                        addView(views.button(t("no")) { finish() })
                    }
                )
            }
        )
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) { finish() }
    }

    private fun read(): Request? = when (intent?.action) {
        WorkSpace.ACTION_ADD_APP -> {
            val pkg = intent.getStringExtra(WorkSpace.EXTRA_PACKAGE)
            if (WorkSpace.isPackageName(pkg) && pkg != null) {
                Request.Add(
                    pkg,
                    WorkSpace.shownLabel(intent.getStringExtra(WorkSpace.EXTRA_LABEL), pkg)
                )
            } else {
                null
            }
        }

        WorkSpace.ACTION_REMOVE -> Request.Remove

        else -> null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val adding = request is Request.Add
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                if (adding) confirm()

            KeyEvent.KEYCODE_Y -> confirm()

            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_ESCAPE -> finish()

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun confirm() {
        when (val asked = request) {
            is Request.Add -> {
                val done = add(asked.packageName)
                val line = t(if (done) "added" else "add_failed", asked.label)
                Toast.makeText(this, line, Toast.LENGTH_SHORT).show()
            }

            Request.Remove -> removeWorkSpace()

            null -> Unit
        }
        finish()
    }

    /** An app the main user has is installed here too, with its own data; a system app is turned on. */
    private fun add(pkg: String): Boolean {
        val admin = SpacesAdminReceiver.component(this)
        val installed = try {
            dpm.installExistingPackage(admin, pkg)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not install $pkg in the Work space", e)
            false
        }
        return installed || enableSystemApp(pkg)
    }

    private fun enableSystemApp(pkg: String): Boolean = try {
        dpm.enableSystemApp(SpacesAdminReceiver.component(this), pkg)
        true
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "$pkg is not a system app", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not turn on $pkg in the Work space", e)
        false
    }

    /** As the profile owner, removing its own data removes the Work space and everything in it. */
    private fun removeWorkSpace() {
        try {
            dpm.wipeData(0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Android refused to remove the Work space", e)
        }
    }

    private companion object {
        const val TAG = "SableSpaces"
    }
}
