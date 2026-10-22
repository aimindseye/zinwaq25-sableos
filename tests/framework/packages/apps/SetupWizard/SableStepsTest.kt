package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the "Step n of N" line carried in patches/framework/packages/apps/SetupWizard/0410. */
class SableStepsTest {
    @Test fun designNumbersWifiAsStepThreeOfEight() {
        assertEquals(listOf(3, 8), SableSteps.position(SableSteps.sectionOf("NetworkSetupActivity"), true)?.toList())
        assertEquals(listOf(1, 8), SableSteps.position(SableSteps.sectionOf("WelcomeActivity"), true)?.toList())
        assertEquals(listOf(8, 8), SableSteps.position(SableSteps.sectionOf("FinishActivity"), true)?.toList())
    }

    @Test fun ownerFlowOrder() {
        val order = listOf(
            "WelcomeActivity" to 1, "LocaleActivity" to 2, "SableKeyboardActivity" to 2,
            "SableKeyboardTestActivity" to 2, "SimMissingActivity" to 3, "NetworkSetupActivity" to 3,
            "SablePrivacyActivity" to 4, "ThemeSettingsActivity" to 5, "DateTimeActivity" to 6,
            "RestoreIntroActivity" to 6, "LocationSettingsActivity" to 6, "UpdateRecoveryActivity" to 6,
            "BiometricActivity" to 7, "ScreenLockActivity" to 7, "NavigationSettingsActivity" to 7,
            "FinishActivity" to 8,
        )
        var last = 0
        for ((name, step) in order) {
            assertEquals(name, step, SableSteps.sectionOf(name))
            assertTrue("$name goes backwards", step >= last)
            last = step
        }
        assertEquals(8, SableSteps.TOTAL)
    }

    @Test fun unnumberedScreensShowNoLine() {
        for (name in listOf("BluetoothSetupActivity", "DeviceSpecificActivity", "LineageSettingsActivity", "", null)) {
            assertEquals(0, SableSteps.sectionOf(name))
            assertNull(SableSteps.position(SableSteps.sectionOf(name), true))
        }
        assertNull(SableSteps.position(9, true))
        assertNull(SableSteps.position(-1, false))
    }

    @Test fun withoutWifiLaterStepsMoveUp() {
        assertEquals(listOf(2, 7), SableSteps.position(SableSteps.LANGUAGE, false)?.toList())
        assertEquals(listOf(2, 7), SableSteps.position(SableSteps.WIFI, false)?.toList())
        assertEquals(listOf(3, 7), SableSteps.position(SableSteps.PRIVACY, false)?.toList())
        assertEquals(listOf(7, 7), SableSteps.position(SableSteps.READY, false)?.toList())
    }
}
