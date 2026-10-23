package org.sableos.messages.core.mms

import java.io.ByteArrayOutputStream

/** Encodes the one PDU Sable Messages sends back to the MMSC after a download. */
object MmsPduEncoder {
    /**
     * M-NotifyResp.ind telling the MMSC the message was retrieved (OMA-TS-MMS_ENC 6.2), so it
     * does not send the notification again. Report-Allowed is "no": Sable sends no delivery
     * reports.
     */
    fun notifyRespInd(
        transactionId: String,
        version: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MmsHeaders.MESSAGE_TYPE)
        out.write(MmsHeaders.MESSAGE_TYPE_NOTIFYRESP_IND)
        out.write(MmsHeaders.TRANSACTION_ID)
        writeText(out, transactionId)
        out.write(MmsHeaders.MMS_VERSION)
        out.write(versionOctet(version))
        out.write(MmsHeaders.STATUS)
        out.write(MmsHeaders.STATUS_RETRIEVED)
        out.write(MmsHeaders.REPORT_ALLOWED)
        out.write(MmsHeaders.VALUE_NO)
        return out.toByteArray()
    }

    /** The notification's version as a short integer; 1.2 when it gave none. */
    private fun versionOctet(version: Int): Int =
        if (version in 1 until PduReader.HIGH_BIT) {
            version or PduReader.HIGH_BIT
        } else {
            MmsHeaders.VERSION_1_2
        }

    private fun writeText(
        out: ByteArrayOutputStream,
        text: String,
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and PduReader.BYTE_MASK) >= PduReader.HIGH_BIT) {
            out.write(PduReader.TEXT_QUOTE)
        }
        out.write(bytes)
        out.write(0)
    }
}
