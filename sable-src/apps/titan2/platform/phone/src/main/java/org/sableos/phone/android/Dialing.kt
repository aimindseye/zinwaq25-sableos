package org.sableos.phone.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import org.sableos.phone.core.CallFormat
import org.sableos.phone.core.PhoneText.t
import org.sableos.phone.core.SpecialCodes

/** Placing calls through Telecom, and the dial pad codes Phone handles itself. */
object Dialing {
    /** Calls [raw] (cleaned to the dialled characters). False when there was nothing to call. */
    fun call(context: Context, raw: String): Boolean {
        val number = CallFormat.dialable(raw)
        if (number.isEmpty()) return false
        place(context, Uri.fromParts("tel", number, null))
        return true
    }

    /** Calls voicemail on the default SIM. */
    fun voicemail(context: Context) = place(context, Uri.fromParts("voicemail", "", null))

    private fun place(context: Context, uri: Uri) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        try {
            telecom.placeCall(uri, Bundle())
        } catch (e: SecurityException) {
            // The call permission was refused: say so instead of failing silently.
            Log.w(TAG, "placeCall refused", e)
            Toast.makeText(context, t("needs_call_permission"), Toast.LENGTH_LONG).show()
        }
    }

    /** Handles [typed] when it is a complete special code; true when it was one (the dial pad then clears). */
    fun special(context: Context, typed: String): Boolean =
        when (val code = SpecialCodes.parse(typed)) {
            null -> false

            SpecialCodes.Code.ShowImei -> {
                Toast.makeText(context, imeis(context), Toast.LENGTH_LONG).show()
                true
            }

            is SpecialCodes.Code.Secret -> {
                runCatching {
                    context.getSystemService(
                        TelephonyManager::class.java
                    ).sendDialerSpecialCode(code.code)
                }
                true
            }
        }

    // READ_PRIVILEGED_PHONE_STATE, granted to the privileged app (privapp allowlist).
    @Suppress("MissingPermission")
    private fun imeis(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val lines = (0 until tm.activeModemCount).mapNotNull { slot ->
            runCatching { tm.getImei(slot) }.getOrNull()?.let { t("imei_line", slot + 1, it) }
        }
        return lines.joinToString("\n").ifEmpty { t("imei_unavailable") }
    }

    private const val TAG = "SablePhone"
}
