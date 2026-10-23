package org.sableos.messages.data

import android.net.Uri

/**
 * Telephony provider columns and URIs. Most are Telephony.* constants; the MMS part/address
 * paths and a few columns are spelled out because the SDK only exposes them on newer levels.
 */
internal object ProviderColumns {
    val THREADS_URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    val CANONICAL_ADDRESSES_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")

    /** Everything the SMS/MMS provider notifies on; the observer watches its descendants. */
    val MMS_SMS_ROOT: Uri = Uri.parse("content://mms-sms/")
    val MMS_PART_URI: Uri = Uri.parse("content://mms/part")

    fun mmsPartsOf(messageId: Long): Uri = Uri.parse("content://mms/$messageId/part")

    fun mmsAddressesOf(messageId: Long): Uri = Uri.parse("content://mms/$messageId/addr")

    fun mmsPart(partId: Long): Uri = Uri.parse("content://mms/part/$partId")

    const val ID = "_id"
    const val THREAD_RECIPIENT_IDS = "recipient_ids"

    const val PART_MESSAGE_ID = "mid"
    const val PART_CONTENT_TYPE = "ct"
    const val PART_CONTENT_ID = "cid"
    const val PART_CONTENT_LOCATION = "cl"
    const val PART_NAME = "name"
    const val PART_FILENAME = "fn"
    const val PART_CHARSET = "chset"
    const val PART_TEXT = "text"

    const val ADDR_ADDRESS = "address"
    const val ADDR_TYPE = "type"
    const val ADDR_CHARSET = "charset"

    /** Value Sable writes to retr_st when an announced MMS could not be downloaded. */
    const val RETRIEVE_STATUS_FAILED = 0xC0
}
