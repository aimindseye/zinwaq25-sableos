package org.sableos.titan2.displaycompat.android

import android.annotation.SuppressLint
import android.content.Context
import org.sableos.titan2.displaycompat.core.CanvasCycle
import org.sableos.titan2.displaycompat.core.HeldCodec
import org.sableos.titan2.displaycompat.core.HeldState
import org.sableos.titan2.displaycompat.core.SystemRotation
import org.sableos.titan2.displaycompat.core.TallCanvas
import org.sableos.titan2.displaycompat.core.ToolsSettings

/**
 * Display tools settings. The held originals live in device-protected storage so the boot
 * receiver can give them back before the user unlocks (a crash while a Tall canvas was shown must
 * not leave the lock screen at 720x1280); they are written with commit(), never apply().
 */
class PrefsToolsSettings(context: Context) : ToolsSettings {
    private val prefs = context.getSharedPreferences(TOOLS, Context.MODE_PRIVATE)

    override var systemRotation: SystemRotation
        get() = SystemRotation.fromId(prefs.getString(KEY_ROTATION, null))
        set(value) {
            prefs.edit().putString(KEY_ROTATION, value.id).apply()
        }

    override var favourites: Set<TallCanvas>
        get() = CanvasCycle.decode(prefs.getString(KEY_FAVOURITES, null))
        set(value) {
            prefs.edit().putString(KEY_FAVOURITES, CanvasCycle.encode(value)).apply()
        }

    private val heldStore = HeldStore(context)

    override var held: HeldState
        get() = heldStore.read()
        set(value) = heldStore.write(value)

    private companion object {
        const val TOOLS = "display_tools"
        const val KEY_ROTATION = "system_rotation"
        const val KEY_FAVOURITES = "canvas_favourites"
    }
}

/** The held originals alone, in device-protected storage (usable before the first unlock). */
class HeldStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(HELD, Context.MODE_PRIVATE)

    fun read(): HeldState = HeldCodec.decode(prefs.getString(KEY_HELD, null))

    // commit(): the originals must be on disk before the display is changed.
    @SuppressLint("ApplySharedPref")
    fun write(h: HeldState) {
        val edit = prefs.edit()
        if (h.isEmpty) edit.remove(KEY_HELD) else edit.putString(KEY_HELD, HeldCodec.encode(h))
        edit.commit()
    }

    private companion object {
        const val HELD = "display_held"
        const val KEY_HELD = "held"
    }
}
