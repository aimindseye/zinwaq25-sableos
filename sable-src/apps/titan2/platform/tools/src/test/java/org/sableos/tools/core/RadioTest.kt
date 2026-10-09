package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Radio Diag's reading/name/consistency tests, carried over with the code it folded into Sable Tools. */
class RadioTest {
    @Test fun everyReadingStateHasDistinctExplicitText() {
        val texts = listOf(
            Reading.Value("READY").text(),
            Reading.NeedsPermission.text(),
            Reading.Unavailable("no service").text(),
            Reading.NotSupported("API").text(),
            Reading.Unknown.text()
        )
        assertEquals(texts.size, texts.toSet().size)
        assertEquals("NEEDS_PERMISSION", Reading.NeedsPermission.text())
        assertEquals("UNAVAILABLE (no service)", Reading.Unavailable("no service").text())
        assertEquals("NOT_SUPPORTED (API)", Reading.NotSupported("API").text())
        assertEquals("UNKNOWN", Reading.Unknown.text())
    }

    @Test fun onlyAValueCountsAsData() {
        assertTrue(Reading.Value("x").isData)
        assertFalse(Reading.NeedsPermission.isData)
        assertFalse(Reading.Unavailable("r").isData)
        assertFalse(Reading.NotSupported("r").isData)
        assertFalse(Reading.Unknown.isData)
    }

    @Test fun unavailableIsNeverRenderedAsOffOrFalse() {
        val t = Reading.Unavailable("no TelephonyManager").text().lowercase()
        assertFalse(t == "false" || t == "off" || t == "-" || t.isEmpty())
    }

    @Test fun networkTypeNamesKnownAndUnrecognisedValues() {
        assertEquals("UNKNOWN", RadioNames.networkType(0))
        assertEquals("LTE", RadioNames.networkType(13))
        assertEquals("IWLAN", RadioNames.networkType(18))
        assertEquals("NR", RadioNames.networkType(20))
        assertEquals("UNRECOGNISED(99)", RadioNames.networkType(99))
        assertEquals("UNRECOGNISED(-5)", RadioNames.networkType(-5))
    }

    @Test fun simStateNamesKnownAndUnrecognisedValues() {
        assertEquals("ABSENT", RadioNames.simState(1))
        assertEquals("READY", RadioNames.simState(5))
        assertEquals("LOADED", RadioNames.simState(10))
        assertEquals("UNRECOGNISED(12)", RadioNames.simState(12))
    }

    @Test fun dataStateNamesIncludeUnknownAndUnrecognised() {
        assertEquals("UNKNOWN", RadioNames.dataState(-1))
        assertEquals("CONNECTED", RadioNames.dataState(2))
        assertEquals("HANDOVER_IN_PROGRESS", RadioNames.dataState(5))
        assertEquals("UNRECOGNISED(7)", RadioNames.dataState(7))
    }

    @Test fun consistencyFlagsSubFeatureWithoutBaseTelephony() {
        val n = Consistency.notes(
            mapOf("telephony" to false, "telephony.ims" to true, "telephony.data" to true),
            emptyMap()
        )
        assertEquals(
            listOf(
                "telephony.data is reported while telephony is not",
                "telephony.ims is reported while telephony is not"
            ),
            n
        )
    }

    @Test fun consistencyFlagsMissingPhonePackageWhenTelephonyIsReported() {
        val n = Consistency.notes(mapOf("telephony" to true), mapOf("com.android.phone" to false))
        assertEquals(1, n.size)
        assertTrue(n[0].contains("missing or not visible"))
    }

    @Test fun consistencyFlagsPhonePackageWithoutTelephonyFeature() {
        val n = Consistency.notes(mapOf("telephony" to false), mapOf("com.android.phone" to true))
        assertEquals(listOf("com.android.phone is present but telephony is not reported"), n)
    }

    @Test fun consistencyIsSilentWhenSourcesAgreeOrAreMissing() {
        assertTrue(
            Consistency.notes(
                mapOf("telephony" to true, "telephony.ims" to true),
                mapOf("com.android.phone" to true)
            ).isEmpty()
        )
        assertTrue(Consistency.notes(emptyMap(), emptyMap()).isEmpty())
        assertTrue(Consistency.notes(mapOf("telephony.ims" to true), emptyMap()).isEmpty())
    }

    @Test fun limitsBlockNeverClaimsImsRegistration() {
        val rows = RadioLimits.rows().associate { it.key to it.value }
        assertEquals("PUBLIC_READ_ONLY_APP_STATE", rows["evidence level"])
        assertTrue(rows.getValue("ims registration").startsWith("NOT_VISIBLE"))
    }

    @Test fun redactionBoundaryIsElevenDigits() {
        assertEquals("1234567890", Redactor.text("1234567890"))
        assertEquals("*******8901", Redactor.text("12345678901"))
    }
}
