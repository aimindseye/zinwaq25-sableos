package org.sableos.titan2.radiodiag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.titan2.radiodiag.core.Consistency
import org.sableos.titan2.radiodiag.core.RadioNames
import org.sableos.titan2.radiodiag.core.RadioReport
import org.sableos.titan2.radiodiag.core.Reading
import org.sableos.titan2.radiodiag.core.Redact
import org.sableos.titan2.radiodiag.core.Row
import org.sableos.titan2.radiodiag.core.Section

class RadioHardeningTest {
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

    @Test fun reportAlwaysCarriesTheLimitsBlock() {
        val r = RadioReport.render("profile unknown", emptyList())
        assertTrue(r.contains("## Limits of this report"))
        assertTrue(r.contains("evidence level: PUBLIC_READ_ONLY_APP_STATE"))
        assertTrue(r.contains("ims registration: NOT_VISIBLE"))
        assertTrue(r.contains("IR-007"))
        assertTrue(r.contains("canonical radio evidence: NOT_PROVIDED_BY_THIS_APP"))
    }

    @Test fun reportNeverStatesImsRegistrationAsObserved() {
        val r = RadioReport.render("p", listOf(Section("SIM", listOf(Row("state", "READY")))))
        val lines = r.lines().filter { it.startsWith("ims registration") }
        assertEquals(1, lines.size)
        assertFalse(lines[0].contains("REGISTERED") && !lines[0].contains("NOT_VISIBLE"))
    }

    @Test fun reportMasksEveryValueNotOnlyProperties() {
        val r = RadioReport.render(
            "p",
            listOf(Section("SIM", listOf(Row("raw", "iccid 89014103211123456789"))))
        )
        assertFalse(r.contains("89014103211123456789"))
        assertTrue(r.contains("6789"))
    }

    @Test fun redactionBoundaryIsElevenDigits() {
        assertEquals("1234567890", Redact.text("1234567890"))
        assertEquals("*******8901", Redact.text("12345678901"))
    }

    @Test fun blankValuesRenderAsDash() {
        val r = RadioReport.render("p", listOf(Section("S", listOf(Row("a", ""), Row("b", "  ")))))
        assertTrue(r.contains("a: -"))
        assertTrue(r.contains("b: -"))
    }

    @Test fun consistencyNotesAppearOnlyWhenPresent() {
        val without = RadioReport.render("p", emptyList())
        assertFalse(without.contains("## Consistency notes"))
        val with = RadioReport.render(
            "p",
            emptyList(),
            listOf("telephony.ims is reported while telephony is not")
        )
        assertTrue(with.contains("## Consistency notes"))
        assertTrue(with.indexOf("## Consistency notes") < with.indexOf("## Limits of this report"))
    }

    @Test fun reportIsDeterministic() {
        val s = listOf(Section("SIM", listOf(Row("state", Reading.Unavailable("x").text()))))
        assertEquals(
            RadioReport.render("p", s, listOf("n")),
            RadioReport.render("p", s, listOf("n"))
        )
    }

    @Test fun unavailableReadingsStayVisibleInTheReport() {
        val s = listOf(
            Section(
                "SIM and network",
                listOf(
                    Row("network type", Reading.NeedsPermission.text()),
                    Row("modems", Reading.Unavailable("no TelephonyManager").text())
                )
            )
        )
        val r = RadioReport.render("p", s)
        assertTrue(r.contains("network type: NEEDS_PERMISSION"))
        assertTrue(r.contains("modems: UNAVAILABLE (no TelephonyManager)"))
    }
}
