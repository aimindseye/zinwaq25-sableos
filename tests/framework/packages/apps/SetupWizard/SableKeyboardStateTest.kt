package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lineageos.setupwizard.sable.SableKeyboardState.Availability
import org.lineageos.setupwizard.sable.SableKeyboardState.Offer

/** Pure tests for the keyboard readiness step carried in patches/framework/packages/apps/SetupWizard/0402. */
class SableKeyboardStateTest {
    private val short = "org.sableos.titan2.keyboard/.android.SableImeService"
    private val long = "org.sableos.titan2.keyboard/org.sableos.titan2.keyboard.android.SableImeService"
    private val latin = "com.android.inputmethod.latin/.LatinIME"

    @Test fun bothIdFormsAreRecognised() {
        assertTrue(SableKeyboardState.isSableIme(short))
        assertTrue(SableKeyboardState.isSableIme(" $long "))
        assertFalse(SableKeyboardState.isSableIme(latin))
        assertFalse(SableKeyboardState.isSableIme("org.sableos.titan2.keyboard/.android.Other"))
        assertFalse(SableKeyboardState.isSableIme("org.sableos.titan2.keyboard/"))
        assertFalse(SableKeyboardState.isSableIme(null))
        assertFalse(SableKeyboardState.isSableIme(""))
    }

    @Test fun classification() {
        assertEquals(Availability.SABLE_SELECTED, SableKeyboardState.classify(true, true, listOf(short), long))
        assertEquals(Availability.SABLE_ENABLED_NOT_SELECTED, SableKeyboardState.classify(true, true, listOf(latin, long), latin))
        assertEquals(Availability.SABLE_NOT_ENABLED, SableKeyboardState.classify(true, true, listOf(latin), latin))
        assertEquals(Availability.UNAVAILABLE, SableKeyboardState.classify(true, false, listOf(latin), latin))
        assertEquals(Availability.UNKNOWN, SableKeyboardState.classify(false, true, listOf(short), short))
        assertEquals(Availability.UNKNOWN, SableKeyboardState.classify(true, true, null, latin))
        assertEquals(Availability.SABLE_SELECTED, SableKeyboardState.classify(true, true, null, short))
    }

    @Test fun eachStateOffersOneFixOrNone() {
        assertEquals(Offer.KEYBOARD_SETTINGS, SableKeyboardState.offer(Availability.SABLE_SELECTED))
        assertEquals(Offer.PICK_KEYBOARD, SableKeyboardState.offer(Availability.SABLE_ENABLED_NOT_SELECTED))
        assertEquals(Offer.INPUT_SETTINGS, SableKeyboardState.offer(Availability.SABLE_NOT_ENABLED))
        assertEquals(Offer.NONE, SableKeyboardState.offer(Availability.UNAVAILABLE))
        assertEquals(Offer.PICK_KEYBOARD, SableKeyboardState.offer(Availability.UNKNOWN))
    }

    @Test fun setupCanAlwaysContinue() {
        Availability.values().forEach { assertTrue(it.name, SableKeyboardState.canContinue(it)) }
    }

    @Test fun aPhysicalKeyboardNeverForcesTheSoftKeyboard() {
        assertFalse(SableKeyboardState.showSoftOnFocus(true))
        assertTrue(SableKeyboardState.showSoftOnFocus(false))
    }
}
