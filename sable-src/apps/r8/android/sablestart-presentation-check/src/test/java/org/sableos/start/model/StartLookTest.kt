package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartLookTest {
    @Test
    fun eachLookSetsTheHomeStyle() {
        assertEquals(HomeStyle.Bb10, StartLook.Bb10.homeStyle)
        assertEquals(HomeStyle.Bb7, StartLook.Bb7.homeStyle)
        assertEquals(HomeStyle.Bb10, StartLook.Plain.homeStyle)
    }

    @Test
    fun plainHidesTheStatusDialsAndTheOthersShowThem() {
        assertEquals(setOf(HomeLayout.SEARCH_ROW, HomeLayout.STATUS_RINGS), StartLook.Plain.hiddenSections(null))
        assertEquals(
            setOf(HomeLayout.SEARCH_ROW),
            StartLook.Bb10.hiddenSections(setOf(HomeLayout.STATUS_RINGS, HomeLayout.SEARCH_ROW)),
        )
    }

    @Test
    fun theUsersOtherHomeChoicesAreKept() {
        // Weather hidden and the search row shown in Edit Home: a look changes only the dials.
        val stored = setOf(HomeLayout.WEATHER)
        assertEquals(setOf(HomeLayout.WEATHER, HomeLayout.STATUS_RINGS), StartLook.Plain.hiddenSections(stored))
        assertEquals(setOf(HomeLayout.WEATHER), StartLook.Bb7.hiddenSections(stored))
    }

    @Test
    fun unknownLooksChangeNothing() {
        assertEquals(StartLook.Bb7, StartLook.fromStableValue("bb7"))
        assertNull(StartLook.fromStableValue("bb6"))
        assertNull(StartLook.fromStableValue(null))
    }
}
