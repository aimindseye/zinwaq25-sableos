package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    @Test fun redactsIdentifiers() {
        assertEquals("imsi ********2345 ok 12345", Redactor.text("imsi 310410012345 ok 12345"))
        assertEquals("mail [email] now", Redactor.text("mail someone@example.org now"))
        assertEquals("wlan [mac]", Redactor.text("wlan 3C:22:FB:01:AB:9E"))
        assertEquals("ip 192.168.1.* gw", Redactor.text("ip 192.168.1.42 gw"))
        assertEquals("v6 fe80:1:…", Redactor.text("v6 fe80:1:0:0:abcd:1234:5678:9abc"))
        assertEquals("call [phone]", Redactor.text("call +1 555-123-4567"))
        assertEquals("at [location]", Redactor.text("at 51.507351, -0.127758"))
        assertEquals("password=[redacted] x", Redactor.text("password=hunter2 x"))
        assertEquals("PIN: [redacted]", Redactor.text("PIN: 7391"))
    }

    @Test fun leavesOrdinaryFactsAlone() {
        val fp = "lineage/lineage_Q25/Q25:16/BP2A.250605.031/eng.root:userdebug/test-keys"
        assertEquals(fp, Redactor.text(fp))
        assertEquals("2026-09-05", Redactor.text("2026-09-05"))
        assertEquals("720x720 193dpi", Redactor.text("720x720 193dpi"))
    }

    @Test fun idMasking() {
        assertEquals("***********3456", Redactor.id("890141032111123456".takeLast(15)))
        assertEquals("-", Redactor.id(null))
        assertEquals("***", Redactor.id("123"))
    }

    @Test fun presetsNeverSelectSensitiveSections() {
        Tool.entries.filter { it.section == Section.REPORTS }.forEach { t ->
            ReportPolicy.preset(t).forEach {
                assertEquals("$t $it", Sensitivity.STANDARD, it.sensitivity)
            }
            ReportPolicy.offered(t).forEach { assertEquals(Sensitivity.SENSITIVE, it.sensitivity) }
        }
    }

    @Test fun planWarnsForSensitiveSelections() {
        val p = ReportPolicy.plan(setOf(ReportCategory.DEVICE, ReportCategory.APPS))
        assertEquals(listOf(ReportCategory.DEVICE, ReportCategory.APPS), p.included)
        assertEquals(1, p.warnings.size)
        assertTrue(ReportCategory.NETWORK in p.notSelected)
        assertTrue("message bodies" in p.neverIncluded)
        assertTrue(ReportPolicy.plan(emptySet()).isEmpty)
    }

    @Test fun renderDropsUnselectedSectionsAndRedacts() {
        val sections = listOf(
            ReportSection(
                ReportCategory.DEVICE,
                "Device",
                listOf(ReportRow("model", "Q25"), ReportRow("blank", ""))
            ),
            ReportSection(ReportCategory.APPS, "Apps", listOf(ReportRow("pkg", "com.bank.secret"))),
            ReportSection(ReportCategory.NETWORK, "Network", listOf(ReportRow("ip", "10.0.0.7")))
        )
        val text = ReportPolicy.render(
            "Sable Tools report",
            "profile zinwa-q25",
            setOf(ReportCategory.DEVICE, ReportCategory.NETWORK),
            sections
        )
        assertTrue(text.contains("## Device"))
        assertTrue(text.contains("blank: -"))
        assertFalse(text.contains("com.bank.secret"))
        assertTrue(text.contains("ip: 10.0.0.*"))
        assertTrue(text.contains("Never included: message bodies"))
    }

    @Test fun textEntrySummaryHasNoTypedText() {
        val rows = TextEntryTest.summary(
            mapOf(
                "pin" to CaseResult.PASS,
                "letters" to CaseResult.FAIL
            )
        )
        assertEquals("1/10 PASS, 1 FAIL, 8 NOT_RUN", rows.first().value)
        assertTrue(rows.none { it.value.contains("7391") })
    }

    @Test fun textEntryChecks() {
        val pin = TextEntryTest.CASES.first { it.id == "pin" }
        assertEquals(CaseResult.PASS, TextEntryTest.check(pin, "7391", emptyList()))
        assertEquals(CaseResult.FAIL, TextEntryTest.check(pin, "739", emptyList()))
        val alt = TextEntryTest.CASES.first { it.id == "alt" }
        assertEquals(
            CaseResult.PASS,
            TextEntryTest.check(alt, "1", listOf(TypedKey(0x2, '1'.code)))
        )
        assertEquals(CaseResult.FAIL, TextEntryTest.check(alt, "w", listOf(TypedKey(0, 'w'.code))))
        assertTrue(TextEntryTest.CASES.any { it.softwareKeyboard })
        assertTrue(TextEntryTest.CASES.any { it.id == "bt_pairing" })
    }

    @Test fun manifestPolicy() {
        val manifest = java.io.File("src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            assertEquals(emptyList<String>(), PrivilegePolicy.violations(manifest.readText()))
        }
        val bad = "<manifest android:sharedUserId=\"android.uid.system\">" +
            "<uses-permission android:name=\"android.permission.INTERNET\"/>" +
            "<application><service android:name=\"x\"/></application></manifest>"
        val v = PrivilegePolicy.violations(bad)
        assertTrue(v.any { it.contains("INTERNET") })
        assertTrue(v.contains("sharedUserId"))
        assertTrue(v.any { it.startsWith("service") })
    }
}
