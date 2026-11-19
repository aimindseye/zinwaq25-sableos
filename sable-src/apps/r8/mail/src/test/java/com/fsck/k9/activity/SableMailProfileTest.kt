package com.fsck.k9.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableMailProfileTest {
    @Test
    fun onlyBb7ReadsAsBb7() {
        assertTrue(SableMailProfile.isBb7("bb7"))
        assertFalse(SableMailProfile.isBb7("bb10"))
        assertFalse(SableMailProfile.isBb7(null))
        assertFalse(SableMailProfile.isBb7("BB7 "))
    }
}
