package org.sableos.titan2.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.titan2.setup.core.Action
import org.sableos.titan2.setup.core.Item
import org.sableos.titan2.setup.core.LaunchOutcome
import org.sableos.titan2.setup.core.LaunchReport
import org.sableos.titan2.setup.core.Observed
import org.sableos.titan2.setup.core.Readiness
import org.sableos.titan2.setup.core.SetupModel
import org.sableos.titan2.setup.core.SetupRows
import org.sableos.titan2.setup.core.Snapshot
import org.sableos.titan2.setup.core.TargetStatus

class SetupHardeningTest {
    private val sableIme = "org.sableos.titan2.keyboard/.android.SableImeService"
    private val value = Observed.Value("pkg.example")

    private fun snap(
        home: Observed = value,
        keyboard: Observed = Observed.Value(sableIme),
        dialer: Observed = value,
        sms: Observed = value
    ) = Snapshot(home, keyboard, dialer, sms)

    private fun rows(
        s: Snapshot = snap(),
        t: (Action) -> TargetStatus = {
            TargetStatus.Resolvable
        }
    ) = SetupRows.build(s, t)

    private fun row(id: String, items: List<Item>) = items.first { it.id == id }

    private val allTexts: List<String>
        get() {
            val every =
                rows() +
                    rows(snap(Observed.Absent, Observed.Absent, Observed.Absent, Observed.Absent)) +
                    rows(snap(Observed.Failed, Observed.Failed, Observed.Failed, Observed.Failed)) +
                    rows(t = { TargetStatus.Missing })
            return every.flatMap { listOf(it.title, it.detail) } +
                listOf(SetupModel.TITLE, SetupModel.SELF_DESCRIPTION, SetupModel.FOOTNOTE) +
                LaunchOutcome.values().mapNotNull { LaunchReport.message("x", it) }
        }

    // --- readiness states, summary, completion ---

    @Test fun allFiveStatesExistAndAreDistinct() {
        assertEquals(
            setOf("Satisfied", "ActionRequired", "Informational", "Unavailable", "Unknown"),
            Readiness.values().map { it.name }.toSet()
        )
    }

    @Test fun allSatisfiedRowsReadyAndSummaryHasNoExtraClauses() {
        val items = rows()
        assertTrue(SetupModel.isReady(items))
        assertEquals("3 of 3 confirmed", SetupModel.summary(items))
    }

    @Test fun summaryNamesUnavailableAndUnknownRows() {
        val items =
            listOf(
                Item("a", "A", "", Readiness.Satisfied),
                Item("b", "B", "", Readiness.Unavailable),
                Item("c", "C", "", Readiness.Unknown),
                Item("d", "D", "", Readiness.ActionRequired)
            )
        assertEquals(
            "1 of 4 confirmed · 1 action required · 1 unavailable · 1 unknown",
            SetupModel.summary(items)
        )
    }

    @Test fun unavailableOrUnknownRowsBlockReady() {
        val ok = listOf(Item("a", "A", "", Readiness.Satisfied))
        assertTrue(SetupModel.isReady(ok))
        assertFalse(SetupModel.isReady(ok + Item("b", "B", "", Readiness.Unavailable)))
        assertFalse(SetupModel.isReady(ok + Item("c", "C", "", Readiness.Unknown)))
        assertFalse(SetupModel.isReady(ok + Item("d", "D", "", Readiness.ActionRequired)))
    }

    @Test fun informationalRowsNeverBlockReadyOrCount() {
        val items =
            listOf(
                Item("a", "A", "", Readiness.Satisfied),
                Item("i", "I", "", Readiness.Informational)
            )
        assertTrue(SetupModel.isReady(items))
        assertEquals("1 of 1 confirmed", SetupModel.summary(items))
    }

    // --- failed reads never become Done ---

    @Test fun failedReadsAreUnknownNeverSatisfiedOrActionRequired() {
        val items = rows(snap(Observed.Failed, Observed.Failed, Observed.Failed, Observed.Failed))
        listOf("ime", "dialer", "sms").forEach {
            assertEquals(it, Readiness.Unknown, row(it, items).state)
        }
        assertEquals(Readiness.Unknown, row("home", items).state)
        assertFalse(SetupModel.isReady(items))
    }

    @Test fun absentIsNotConfiguredOnlyWhenTheReadSucceeded() {
        val items = rows(snap(dialer = Observed.Absent, sms = Observed.Absent))
        assertEquals(Readiness.ActionRequired, row("dialer", items).state)
        assertEquals(Readiness.ActionRequired, row("sms", items).state)
    }

    @Test fun blankKeyboardValueIsUnknownNotNotConfigured() {
        assertEquals(Readiness.Unknown, row("ime", rows(snap(keyboard = Observed.Absent))).state)
    }

    @Test fun anotherKeyboardIsActionRequiredAndSableKeyboardIsSatisfied() {
        assertEquals(Readiness.Satisfied, row("ime", rows()).state)
        val other = rows(snap(keyboard = Observed.Value("com.other.ime/.Service")))
        assertEquals(Readiness.ActionRequired, row("ime", other).state)
        val lookalike = rows(snap(keyboard = Observed.Value("org.sableos.titan2.keyboardx/.S")))
        assertEquals(Readiness.ActionRequired, row("ime", lookalike).state)
    }

    // --- launch targets ---

    @Test fun missingTargetOnInformationalRowIsVisibleAsUnavailable() {
        val items = rows(t = {
            if (it ==
                Action.DisplayCompat
            ) {
                TargetStatus.Missing
            } else {
                TargetStatus.Resolvable
            }
        })
        val r = row("display", items)
        assertEquals(Readiness.Unavailable, r.state)
        assertEquals(Action.None, r.action)
        assertNull(SetupRows.buttonLabel(r))
        assertTrue(r.detail.contains("Not available"))
        assertTrue(SetupModel.summary(items).contains("1 unavailable"))
        assertFalse(SetupModel.isReady(items))
    }

    @Test fun missingTargetOnActionRequiredRowStaysVisibleAndIsNotOfferedAsFix() {
        val items =
            rows(snap(dialer = Observed.Absent)) {
                if (it ==
                    Action.DefaultApps
                ) {
                    TargetStatus.Missing
                } else {
                    TargetStatus.Resolvable
                }
            }
        val r = row("dialer", items)
        assertEquals(Readiness.Unavailable, r.state)
        assertTrue(r.detail.startsWith("No default app is configured"))
        assertTrue(r.detail.contains("not available on this device"))
        assertNull(SetupRows.buttonLabel(r))
    }

    @Test fun missingTargetNeverDowngradesASatisfiedOrUnknownRow() {
        val all = rows(snap(sms = Observed.Failed)) { TargetStatus.Missing }
        assertEquals(Readiness.Satisfied, row("dialer", all).state)
        assertEquals(Action.None, row("dialer", all).action)
        assertEquals(Readiness.Unknown, row("sms", all).state)
        assertEquals(Action.None, row("sms", all).action)
    }

    @Test fun unverifiableTargetIsNotOfferedAsUsable() {
        val items = rows { TargetStatus.Unverifiable }
        val display = row("display", items)
        assertEquals(Readiness.Informational, display.state)
        assertEquals(Action.None, display.action)
        assertNull(SetupRows.buttonLabel(display))
        assertTrue(display.detail.contains("could not verify"))
    }

    @Test fun unverifiableFixTargetKeepsTheEvidenceButRemovesTheButton() {
        val items =
            rows(snap(dialer = Observed.Absent)) {
                if (it == Action.DefaultApps) {
                    TargetStatus.Unverifiable
                } else {
                    TargetStatus.Resolvable
                }
            }
        val dialer = row("dialer", items)
        assertEquals(Readiness.ActionRequired, dialer.state)
        assertEquals(Action.None, dialer.action)
        assertNull(SetupRows.buttonLabel(dialer))
        assertTrue(dialer.detail.contains("could not verify"))
    }

    @Test fun everyDestinationIsCoveredByARow() {
        val used = rows().map { it.action }.toSet()
        assertEquals(Action.values().toSet() - Action.None, used)
    }

    @Test fun buttonLabels() {
        val r = rows(snap(dialer = Observed.Absent))
        assertEquals("Fix", SetupRows.buttonLabel(row("dialer", r)))
        assertEquals("Open", SetupRows.buttonLabel(row("display", r)))
        assertNull(SetupRows.buttonLabel(row("ime", r)))
    }

    // --- launch failures stay bounded ---

    @Test fun successfulLaunchShowsNothing() {
        assertNull(LaunchReport.message("Home settings", LaunchOutcome.Launched))
    }

    @Test fun everyFailureProducesAShortMessage() {
        LaunchOutcome.values().filter { it != LaunchOutcome.Launched }.forEach {
            val m = LaunchReport.message("Home settings", it)
            assertNotNull(m)
            assertTrue(m!!.length <= LaunchReport.MAX_MESSAGE)
            assertTrue(m.contains("Home settings"))
        }
    }

    @Test fun anAbsurdlyLongLabelCannotUnboundTheMessage() {
        val m = LaunchReport.message("x".repeat(5000), LaunchOutcome.Failed)!!
        assertTrue(m.length <= LaunchReport.MAX_MESSAGE)
    }

    @Test fun blankLabelStillProducesAReadableMessage() {
        assertEquals(
            "Could not open that screen: it is not available on this device.",
            LaunchReport.message("  ", LaunchOutcome.TargetMissing)
        )
    }

    @Test fun missingAndDeniedAreDistinguished() {
        val missing = LaunchReport.message("A", LaunchOutcome.TargetMissing)
        val denied = LaunchReport.message("A", LaunchOutcome.Denied)
        assertTrue(missing != denied)
        assertTrue(missing!!.contains("not available"))
        assertTrue(denied!!.contains("blocked"))
    }

    // --- IMS wording ---

    @Test fun radioRowDoesNotClaimImsRegistrationIsVisible() {
        val r = row("radio", rows())
        assertEquals(
            "Read-only SIM and network diagnostics; IMS registration visibility requires canonical IR-007",
            r.detail
        )
        assertFalse(r.title.contains("IMS"))
        assertFalse(r.detail.contains("IMS status"))
    }

    @Test fun everyImsMentionIsTheIr007Qualifier() {
        allTexts.filter { it.contains("IMS") }.forEach {
            assertTrue(it, it.contains("IMS registration visibility requires canonical IR-007"))
        }
    }

    @Test fun radioRowIsNotAnImsObservation() {
        val r = row("radio", rows())
        assertEquals(Readiness.Informational, r.state)
        assertFalse(r.detail.lowercase().contains("ims registered"))
    }

    // --- not a Setup Wizard ---

    @Test fun helperDescribesItselfAsNotTheSetupWizard() {
        assertTrue(SetupModel.SELF_DESCRIPTION.contains("Not the Android Setup Wizard"))
        assertTrue(SetupModel.SELF_DESCRIPTION.contains("Post-provisioning"))
    }

    @Test fun noTextClaimsWizardFirstBootOrProvisioningAuthority() {
        allTexts.forEach {
            val t = it.lowercase()
            if (t.contains("wizard")) assertTrue(it, t.contains("not the android setup wizard"))
            assertFalse(
                it,
                t.contains("first-boot") || t.contains("first boot") || t.contains("first-run")
            )
            assertFalse(it, t.contains("provisioning complete") || t.contains("provisioned"))
        }
    }

    // --- default-app wording matches what is tested ---

    @Test fun dialerAndSmsOnlyClaimThatADefaultAppIsConfigured() {
        val items = rows()
        listOf("dialer", "sms").forEach {
            val r = row(it, items)
            assertTrue(r.detail.startsWith("A default app is configured: pkg.example"))
            assertTrue(r.detail.contains("not checked against a specific app"))
            val lower = (r.title + r.detail).lowercase()
            assertFalse(
                lower.contains("correct") || lower.contains("sable") || lower.contains("is the ")
            )
        }
    }

    @Test fun anyDefaultPackageIsSatisfiedBecauseOnlyPresenceIsTested() {
        val items =
            rows(
                snap(
                    dialer = Observed.Value("com.someone.else"),
                    sms = Observed.Value("org.sableos.x")
                )
            )
        assertEquals(Readiness.Satisfied, row("dialer", items).state)
        assertEquals(Readiness.Satisfied, row("sms", items).state)
        assertTrue(row("dialer", items).detail.contains("com.someone.else"))
    }

    @Test fun homeIsInformationalWhateverIsObserved() {
        listOf(Observed.Value("com.any.launcher"), Observed.Absent).forEach {
            val r = row("home", rows(snap(home = it)))
            assertEquals(Readiness.Informational, r.state)
        }
        assertEquals(Action.HomeSettings, row("home", rows()).action)
    }

    @Test fun homeDetailNeverCallsAPackageCorrectOrWrong() {
        val d = row(
            "home",
            rows(snap(home = Observed.Value("com.any.launcher")))
        ).detail.lowercase()
        assertFalse(d.contains("correct") || d.contains("wrong") || d.contains("expected"))
    }

    @Test fun footnoteKeepsCanonicalLedgersCanonical() {
        listOf("IR-001", "IR-003", "IR-005").forEach {
            assertTrue(SetupModel.FOOTNOTE.contains(it))
        }
        assertTrue(SetupModel.FOOTNOTE.contains("Observation only"))
    }

    // --- profile identity ---

    @Test fun blankNullAndWhitespaceProfilesAreExplicitUnknown() {
        listOf(null, "", "   ", "\n\t").forEach {
            assertEquals("Profile unknown (not reported by this build)", SetupModel.profileLine(it))
        }
    }

    @Test fun knownProfilesAreLabelledByIdOnly() {
        assertEquals("Unihertz Titan 2", SetupModel.profileLine(" titan2 "))
        assertEquals("Unihertz Titan 2 Elite", SetupModel.profileLine("titan2-elite"))
    }

    @Test fun deviceNamesAreNotProfileIds() {
        assertEquals("Unrecognized profile id: Unihertz", SetupModel.profileLine("Unihertz"))
        assertEquals("Unrecognized profile id: Titan2", SetupModel.profileLine("Titan2"))
    }

    @Test fun unrecognizedProfileTextIsSanitizedAndBounded() {
        val m = SetupModel.profileLine("a".repeat(500) + "<script>")
        assertTrue(m.length <= "Unrecognized profile id: ".length + SetupModel.MAX_PROFILE_ID)
        assertEquals("Unrecognized profile id: a-b_c.1", SetupModel.profileLine("a-b_c.1\u0000\n"))
        assertEquals("Profile unknown (not reported by this build)", SetupModel.profileLine("!!!"))
    }

    @Test fun profileDoesNotChangeAnyRow() {
        val a = SetupRows.build(snap()) { TargetStatus.Resolvable }
        SetupModel.profileLine("titan2-elite")
        assertEquals(a, SetupRows.build(snap()) { TargetStatus.Resolvable })
    }
}
