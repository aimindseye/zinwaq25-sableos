package org.sableos.assistant.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import org.sableos.assistant.core.Butler

/** One number to text, with the name the owner will see on the confirm card. */
data class Recipient(
    val name: String,
    val number: String,
)

/** How sending went. */
enum class SmsResult { Sent, NoSim, NotAllowed, Failed }

/**
 * Cosgrove's hands on the phone: find who a text is for, and send it once the owner has confirmed.
 * Contacts are read on the phone and never reach the model. SEND_SMS comes with the Assistant role
 * (SableFrameworkOverlay makes Cosgrove the default assistant) and default-permissions-sable-q25.
 */
class PhoneActions(
    private val context: Context,
) {
    fun canReadContacts(): Boolean = granted(Manifest.permission.READ_CONTACTS)

    fun canSendSms(): Boolean = granted(Manifest.permission.SEND_SMS)

    /** Contacts whose name matches [to] (a number is its own recipient), best first, at most [MAX]. */
    fun recipients(to: String): List<Recipient> =
        when {
            Butler.isNumber(to) -> listOf(Recipient(to.trim(), to.trim()))
            canReadContacts() -> contacts(to)
            else -> emptyList()
        }

    private fun contacts(to: String): List<Recipient> {
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(to.trim()))
        val columns =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
        val found = mutableListOf<Recipient>()
        runCatching {
            context.contentResolver
                .query(uri, columns, null, null, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC")
                ?.use { c ->
                    while (c.moveToNext() && found.size < MAX) {
                        val name = c.getString(0).orEmpty()
                        val number = c.getString(1).orEmpty()
                        if (number.isNotBlank() && found.none { it.number == number }) found += Recipient(name, number)
                    }
                }
        }
        return found
    }

    /** Sends [body] to [number] from the default SMS SIM. Only after the owner pressed Send. */
    fun sendSms(
        number: String,
        body: String,
    ): SmsResult {
        val subscription = SubscriptionManager.getDefaultSmsSubscriptionId()
        return when {
            !canSendSms() -> SmsResult.NotAllowed
            subscription == SubscriptionManager.INVALID_SUBSCRIPTION_ID -> SmsResult.NoSim
            else -> send(subscription, number, body)
        }
    }

    // sendSms checks SEND_SMS (canSendSms) before it calls this.
    @SuppressLint("MissingPermission")
    private fun send(
        subscription: Int,
        number: String,
        body: String,
    ): SmsResult =
        runCatching {
            val sms =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscription)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subscription)
                }
            val parts = sms.divideMessage(body)
            if (parts.size == 1) {
                sms.sendTextMessage(number, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(number, null, parts, null, null)
            }
            SmsResult.Sent
        }.getOrDefault(SmsResult.Failed)

    /** The text in the Messages app instead, for the owner to send there. */
    fun draftInMessages(
        number: String,
        body: String,
    ): Boolean =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                    .putExtra("sms_body", body)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val MAX = 4
    }
}
