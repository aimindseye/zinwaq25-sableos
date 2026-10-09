package org.sableos.reference.setupflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowStepsTest {
    private val networks = listOf(WifiNetwork("Home", true), WifiNetwork("Cafe", false))
    private val flow = Steps.canonical(networks)

    // --- no dead ends ---

    @Test fun everyStepOffersAnExitForwardBackOrSkip() {
        flow.forEach { assertTrue(it.id, it.hasExit) }
    }

    @Test fun everyStepHasAKeyboardReachableControl() {
        flow.forEach { assertTrue(it.id, it.controls.any { c -> c.enabled }) }
    }

    @Test fun onlyTheFirstStepCannotGoBack() {
        assertEquals(listOf("welcome"), flow.filter { !it.canGoBack }.map { it.id })
    }

    @Test fun controlIdsAreUniqueWithinAStep() {
        flow.forEach {
            assertEquals(it.id, it.controls.size, it.controls.map { c -> c.id }.toSet().size)
        }
    }

    // --- Wi-Fi ---

    @Test fun everyVisibleNetworkAndAddNetworkAreKeyboardListItems() {
        val step = Steps.wifiList(networks)
        assertEquals(
            listOf("wifi:Home", "wifi:Cafe", Steps.ADD_NETWORK),
            step.controls.take(3).map {
                it.id
            }
        )
        assertTrue(step.controls.take(3).all { it.kind == ControlKind.ListItem })
    }

    @Test fun anEmptyNetworkListStillLeavesAddNetworkAndSkip() {
        val step = Steps.wifiList(emptyList())
        assertEquals(listOf(Steps.ADD_NETWORK, "wifi_skip"), step.controls.map { it.id })
        assertTrue(step.hasExit)
    }

    @Test fun wifiSkipFollowsPolicyButTheStepStaysExitable() {
        val locked = Steps.wifiList(networks, FlowPolicy(wifiSkippable = false))
        assertEquals(null, locked.skipId)
        assertFalse(locked.controls.first { it.id == "wifi_skip" }.enabled)
        assertTrue(locked.hasExit)
        assertEquals("wifi_skip", Steps.wifiList(networks).skipId)
    }

    @Test fun wifiPasswordHasATextFieldARevealSwitchAndAConnectAction() {
        val step = Steps.wifiPassword()
        assertEquals(
            listOf(ControlKind.TextField, ControlKind.Switch, ControlKind.Button),
            step.controls.map {
                it.kind
            }
        )
        assertEquals("connect", step.primaryId)
    }

    // --- account ---

    @Test fun accountHasTwoTextFieldsAndSkipFollowsPolicy() {
        assertEquals(2, Steps.account().controls.count { it.kind == ControlKind.TextField })
        assertEquals("account_skip", Steps.account().skipId)
        assertEquals(null, Steps.account(FlowPolicy(accountSkippable = false)).skipId)
    }

    // --- SIM never dead-ends ---

    @Test fun simCanAlwaysContinueInEveryState() {
        SimState.values().forEach { assertTrue(it.name, SimStep.canContinue(it)) }
        assertTrue(FlowPolicy(wifiSkippable = false, accountSkippable = false).simSkippable)
    }

    @Test fun simStepSkipsWithItsPrimaryActionAndNeverRequiresRegistration() {
        val step = Steps.sim()
        assertEquals(step.primaryId, step.skipId)
        assertTrue(step.hasExit)
    }

    @Test fun simMessagesAreHonestAndNeverMentionImsOrBlock() {
        SimState.values().forEach {
            val m = SimStep.message(it)
            assertFalse(m, m.contains("IMS", ignoreCase = true))
            if (it != SimState.Registered) assertTrue(m, m.contains("continue", ignoreCase = true))
        }
        assertEquals("Mobile network is connected.", SimStep.message(SimState.Registered))
    }

    @Test fun onlyARegisteredStateClaimsConnected() {
        SimState.values().filter { it != SimState.Registered }.forEach {
            assertFalse(SimStep.message(it).contains("is connected."))
        }
    }

    @Test fun unknownAndErrorDoNotClaimTheNetworkIsNotConnected() {
        assertFalse(SimStep.message(SimState.Unknown).contains("not connected"))
        assertFalse(SimStep.message(SimState.Error).contains("not connected"))
    }

    // --- keyboard availability and software fallback ---

    @Test fun provisionedSableKeyboardIsUsed() {
        assertEquals(
            TextKeyboard.Sable,
            TextEntry.plan(KeyboardAvailability.SableSelected, true).keyboard
        )
    }

    @Test fun everyOtherAvailabilityFallsBackToTheStockSoftwareKeyboard() {
        listOf(
            KeyboardAvailability.SableAvailableNotSelected,
            KeyboardAvailability.Unavailable,
            KeyboardAvailability.Unknown
        )
            .forEach {
                assertEquals(
                    it.name,
                    TextKeyboard.StockSoftware,
                    TextEntry.plan(it, false).keyboard
                )
            }
    }

    @Test fun textEntryIsNeverADeadEnd() {
        KeyboardAvailability.values().forEach { a ->
            listOf(true, false).forEach { assertFalse(TextEntry.plan(a, it).deadEnd) }
        }
    }

    @Test fun aPhysicalKeyboardDoesNotForceTheSoftKeyboardOpen() {
        assertFalse(
            TextEntry.plan(
                KeyboardAvailability.SableSelected,
                physicalKeyboard = true
            ).showSoftOnFocus
        )
        assertTrue(
            TextEntry.plan(
                KeyboardAvailability.SableSelected,
                physicalKeyboard = false
            ).showSoftOnFocus
        )
        assertTrue(
            TextEntry.plan(
                KeyboardAvailability.Unavailable,
                physicalKeyboard = false
            ).showSoftOnFocus
        )
    }

    @Test fun keyboardStepAlwaysLetsSetupContinue() {
        val step = Steps.keyboardChoice()
        assertEquals("keyboard_continue", step.primaryId)
        assertNotNull(step.controls.firstOrNull { it.id == "keyboard_continue" && it.enabled })
    }

    @Test fun welcomeLeadsForwardWithoutAnyAccountOrRadioPrecondition() {
        val step = Steps.welcome()
        assertEquals("start", step.primaryId)
        assertTrue(step.controls.all { it.enabled })
    }
}
