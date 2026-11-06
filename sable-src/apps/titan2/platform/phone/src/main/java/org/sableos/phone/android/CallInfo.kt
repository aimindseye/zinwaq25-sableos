package org.sableos.phone.android

import android.content.Context
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale
import org.sableos.phone.core.PhoneText.t

/** Who a call is with and what state it is in, in the words the call screen and notifications use. */
object CallInfo {
    fun number(call: Call): String = call.details.handle?.schemeSpecificPart.orEmpty()

    fun hidden(call: Call): Boolean =
        call.details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED &&
            number(call).isEmpty()

    /** The contact's name, else the network's caller name, else the formatted number, else "Private number". */
    fun name(context: Context, call: Call): String {
        val d = call.details
        if (d.hasProperty(Call.Details.PROPERTY_CONFERENCE)) return t("conference")
        return d.contactDisplayName?.takeIf { it.isNotBlank() }
            ?: d.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: formatted(context, number(call)).ifEmpty { t("private_number") }
    }

    /** The number under the name, when the name is a contact's; empty otherwise. */
    fun subtitle(context: Context, call: Call): String {
        val d = call.details
        val n = number(call)
        return if (!d.contactDisplayName.isNullOrBlank() &&
            n.isNotEmpty()
        ) {
            formatted(context, n)
        } else {
            ""
        }
    }

    fun formatted(context: Context, number: String): String {
        if (number.isEmpty()) return ""
        val iso = context.getSystemService(TelephonyManager::class.java)?.networkCountryIso
            ?.uppercase(Locale.ROOT)?.ifEmpty { null } ?: Locale.getDefault().country
        return PhoneNumberUtils.formatNumber(number, iso) ?: number
    }

    /** The line under the caller: "Calling…", "On hold", or empty while the timer shows. */
    fun stateLabel(call: Call): String = when (call.details.state) {
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> t("incoming_call")

        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_PULLING_CALL -> t(
            "calling"
        )

        Call.STATE_HOLDING -> t("on_hold")

        Call.STATE_DISCONNECTING -> t("ending")

        Call.STATE_DISCONNECTED -> t("call_ended")

        Call.STATE_SELECT_PHONE_ACCOUNT -> t("choose_sim")

        else -> ""
    }
}
