package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarTest {
    @Test
    fun initialsComeFromTheFirstTwoWords() {
        assertEquals("AB", Avatar.initials("ada byron king"))
        assertEquals("M", Avatar.initials("  Mum "))
        assertEquals("JS", Avatar.initials("(Jo) Smith"))
    }

    @Test
    fun numbersAndEmptyNamesShowAHash() {
        assertEquals("#", Avatar.initials("+44 20 7946 0000"))
        assertEquals("#", Avatar.initials(""))
    }
}
