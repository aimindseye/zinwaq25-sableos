package org.sableos.messages.core

/**
 * Provider and SmsManager codes to bubble states. The numbers are the public values of
 * Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*, Telephony.BaseMmsColumns.MESSAGE_BOX_*,
 * PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND and SmsManager.RESULT_ERROR_*.
 */
object MessageStatus {
    const val SMS_TYPE_INBOX = 1
    const val SMS_TYPE_SENT = 2
    const val SMS_TYPE_DRAFT = 3
    const val SMS_TYPE_OUTBOX = 4
    const val SMS_TYPE_FAILED = 5
    const val SMS_TYPE_QUEUED = 6

    const val MMS_BOX_INBOX = 1
    const val MMS_BOX_SENT = 2
    const val MMS_BOX_DRAFTS = 3
    const val MMS_BOX_OUTBOX = 4
    const val MMS_BOX_FAILED = 5

    const val MMS_TYPE_NOTIFICATION_IND = 0x82

    /** Activity.RESULT_OK, what a sent PendingIntent reports when the part went out. */
    const val RESULT_OK = -1

    /** SmsManager.RESULT_ERROR_GENERIC_FAILURE. */
    const val ERROR_GENERIC = 1
    private const val ERROR_RADIO_OFF = 2
    private const val ERROR_NULL_PDU = 3
    private const val ERROR_NO_SERVICE = 4
    private const val ERROR_LIMIT_EXCEEDED = 5
    private const val ERROR_FDN = 6
    private const val ERROR_SHORT_CODE_NOT_ALLOWED = 7
    private const val ERROR_SHORT_CODE_NEVER_ALLOWED = 8
    private const val ERROR_RADIO_NOT_AVAILABLE = 9
    private const val ERROR_NETWORK_REJECT = 10

    fun smsDirection(type: Int): Direction = if (type == SMS_TYPE_INBOX) Direction.Incoming else Direction.Outgoing

    fun smsState(type: Int): DeliveryState =
        when (type) {
            SMS_TYPE_SENT -> DeliveryState.Sent
            SMS_TYPE_DRAFT -> DeliveryState.Draft
            SMS_TYPE_OUTBOX, SMS_TYPE_QUEUED -> DeliveryState.Sending
            SMS_TYPE_FAILED -> DeliveryState.Failed
            else -> DeliveryState.Received
        }

    fun mmsDirection(box: Int): Direction = if (box == MMS_BOX_INBOX) Direction.Incoming else Direction.Outgoing

    /** [retrieveFailed]: an M-Notification.ind whose download failed (Sable sets retr_st). */
    fun mmsState(
        box: Int,
        messageType: Int,
        retrieveFailed: Boolean,
    ): DeliveryState =
        when {
            messageType == MMS_TYPE_NOTIFICATION_IND && retrieveFailed -> DeliveryState.DownloadFailed
            messageType == MMS_TYPE_NOTIFICATION_IND -> DeliveryState.Downloading
            box == MMS_BOX_SENT -> DeliveryState.Sent
            box == MMS_BOX_DRAFTS -> DeliveryState.Draft
            box == MMS_BOX_OUTBOX -> DeliveryState.Sending
            box == MMS_BOX_FAILED -> DeliveryState.Failed
            else -> DeliveryState.Received
        }

    /**
     * New provider type of a sent message after one part's sent-PendingIntent result. A failure is
     * final; the message is sent once its last part went out (parts are reported in order).
     */
    fun afterPartResult(
        currentType: Int,
        resultCode: Int,
        isLastPart: Boolean,
    ): Int =
        when {
            currentType == SMS_TYPE_FAILED -> SMS_TYPE_FAILED
            resultCode != RESULT_OK -> SMS_TYPE_FAILED
            currentType == SMS_TYPE_SENT || isLastPart -> SMS_TYPE_SENT
            else -> SMS_TYPE_OUTBOX
        }

    /** Short line under a bubble. */
    fun label(
        state: DeliveryState,
        errorCode: Int,
        time: String,
    ): String =
        when (state) {
            DeliveryState.Received -> time
            DeliveryState.Sending -> "Sending…"
            DeliveryState.Sent -> "Sent $time"
            DeliveryState.Draft -> "Draft"
            DeliveryState.Failed -> "Not sent · ${failureReason(errorCode)} · Enter to retry"
            DeliveryState.Downloading -> "Multimedia message · downloading…"
            DeliveryState.DownloadFailed -> "Multimedia message not downloaded · Enter to retry"
        }

    fun failureReason(errorCode: Int): String =
        when (errorCode) {
            0, ERROR_GENERIC, ERROR_NULL_PDU -> "couldn't send"
            ERROR_RADIO_OFF -> "radio off"
            ERROR_NO_SERVICE -> "no service"
            ERROR_LIMIT_EXCEEDED -> "sending limit reached"
            ERROR_FDN -> "blocked by fixed dialing"
            ERROR_SHORT_CODE_NOT_ALLOWED, ERROR_SHORT_CODE_NEVER_ALLOWED -> "short code not allowed"
            ERROR_RADIO_NOT_AVAILABLE -> "radio not available"
            ERROR_NETWORK_REJECT -> "network rejected it"
            else -> "error $errorCode"
        }
}
