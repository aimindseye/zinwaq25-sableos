package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SableAppSetTest {
    private val sable = SableAppSet.ESSENTIAL + SableAppSet.ALWAYS + SableAppSet.RECOMMENDED + SableAppSet.OPTIONAL
    private val stock = setOf("org.lineageos.aperture", "org.lineageos.jelly", "org.lineageos.glimpse")
    private val image = sable + stock

    @Test
    fun fullSableTurnsEverySableAppOnAndLeavesStockAppsAlone() {
        val plan = SableAppSet.plan(SableAppSet.FULL, image)
        assertTrue(sable.all { plan[it] == true })
        stock.forEach { assertNull(it, plan[it]) }
    }

    @Test
    fun essentialsKeepsCoreAndCameraAndTurnsTheRestOff() {
        val plan = SableAppSet.plan(SableAppSet.ESSENTIALS, image)
        listOf(
            "org.sableos.launcher", "org.sableos.titan2.keyboard", "org.sableos.titan2.setup",
            "org.sableos.titan2.displaycompat", "org.sableos.tools", "org.sableos.hub",
            "org.sableos.messages", "org.sableos.titan2.vault", "org.sableos.titan2.profiles",
            "org.sableos.titan2.camera",
        ).forEach { assertEquals(it, true, plan[it]) }
        listOf("org.sableos.calendar", "org.sableos.notes", "org.sableos.titan2.compass", "org.sableos.weather")
            .forEach { assertEquals(it, false, plan[it]) }
        // The browser is off, so Jelly, which stands in for it, is on.
        assertEquals(false, plan["org.sableos.browser"])
        assertEquals(true, plan["org.lineageos.jelly"])
        // Camera stays Sable's, so Aperture is left as it is.
        assertNull(plan["org.lineageos.aperture"])
    }

    @Test
    fun stockAndroidUsesTheLineageAppWhereThePhoneHasOne() {
        val plan = SableAppSet.plan(SableAppSet.STOCK, image)
        assertEquals(false, plan["org.sableos.titan2.camera"])
        assertEquals(true, plan["org.lineageos.aperture"])
        assertEquals(false, plan["org.sableos.browser"])
        assertEquals(false, plan["org.sableos.hub"])
        assertEquals(false, plan["org.sableos.notes"])
        // No other SMS app in the image: Sable Messages stays on.
        assertEquals(true, plan["org.sableos.messages"])
        // With AOSP Messaging in the image it goes off and Messaging comes on.
        val withMessaging = SableAppSet.plan(SableAppSet.STOCK, image + "com.android.messaging")
        assertEquals(false, withMessaging["org.sableos.messages"])
        assertEquals(true, withMessaging["com.android.messaging"])
    }

    @Test
    fun homeKeyboardSetupAndAccessibilityAreNeverTurnedOff() {
        listOf(SableAppSet.FULL, SableAppSet.ESSENTIALS, SableAppSet.STOCK).forEach { choice ->
            val plan = SableAppSet.plan(choice, image)
            SableAppSet.ALWAYS.forEach { assertEquals("$choice $it", true, plan[it]) }
        }
    }

    @Test
    fun onlyInstalledAppsAreTouched() {
        val plan = SableAppSet.plan(SableAppSet.STOCK, setOf("org.sableos.notes", "org.sableos.launcher"))
        assertEquals(setOf("org.sableos.notes", "org.sableos.launcher"), plan.keys)
        assertFalse(plan.containsKey("org.lineageos.jelly"))
    }

    @Test
    fun theChoiceIsRememberedAndAnUnknownValueMeansFull() {
        listOf(SableAppSet.FULL, SableAppSet.ESSENTIALS, SableAppSet.STOCK).forEach {
            assertEquals(it, SableAppSet.choiceOf(SableAppSet.storedValueOf(it)))
        }
        assertEquals(SableAppSet.FULL, SableAppSet.choiceOf(null))
        assertEquals(SableAppSet.FULL, SableAppSet.choiceOf("everything"))
    }

    @Test
    fun theTiersDoNotOverlapAndEssentialsIsCorePlusCamera() {
        val tiers = listOf(SableAppSet.CORE.toList(), SableAppSet.RECOMMENDED, SableAppSet.OPTIONAL)
        assertEquals(tiers.sumOf { it.size }, tiers.flatten().toSet().size)
        assertEquals(SableAppSet.CORE + "org.sableos.titan2.camera", SableAppSet.ESSENTIAL)
        assertTrue(SableAppSet.ALWAYS.all { it in SableAppSet.CORE })
    }

    @Test
    fun phoneAndContactsAreNeverTouched() {
        val phone = setOf("org.sableos.phone", "com.android.contacts", "com.android.contactspicker")
        listOf(SableAppSet.FULL, SableAppSet.ESSENTIALS, SableAppSet.STOCK).forEach { choice ->
            assertTrue(SableAppSet.plan(choice, image + phone).keys.none { it in phone })
        }
    }

    @Test
    fun onlyRecommendedAndOptionalAppsAreCustomizable() {
        val list = SableAppSet.customizable(image)
        assertEquals((SableAppSet.RECOMMENDED + SableAppSet.OPTIONAL).toSet(), list.toSet())
        assertTrue(list.none { it in SableAppSet.CORE })
    }

    @Test
    fun theCheckBoxesOverrideTheSetButNeverCoreApps() {
        val wanted = mapOf(
            "org.sableos.notes" to false, "org.sableos.assistant" to true,
            "org.sableos.hub" to false, "org.sableos.browser" to false,
        )
        val plan = SableAppSet.plan(SableAppSet.ESSENTIALS, image, wanted)
        assertEquals(false, plan["org.sableos.notes"])
        assertEquals(true, plan["org.sableos.assistant"])
        // Core is not customizable: Hub stays on.
        assertEquals(true, plan["org.sableos.hub"])
        // An unticked Browser turns its LineageOS counterpart on.
        assertEquals(true, plan["org.lineageos.jelly"])
    }

    @Test
    fun noCheckBoxesMeansThePlainSet() {
        listOf(SableAppSet.FULL, SableAppSet.ESSENTIALS, SableAppSet.STOCK).forEach {
            assertEquals(SableAppSet.plan(it, image), SableAppSet.plan(it, image, emptyMap()))
        }
    }
}
