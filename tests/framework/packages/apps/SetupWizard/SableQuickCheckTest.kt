package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lineageos.setupwizard.sable.SableQuickCheck.Result

/** Pure tests for the Welcome keyboard check carried in patches/framework/packages/apps/SetupWizard/0411. */
class SableQuickCheckTest {
    @Test fun designTextPasses() {
        for (typed in listOf("A7@", "A 7 @", "a7@", " A\t7 @ ", "A 7@")) {
            assertEquals(typed, Result.PASS, SableQuickCheck.evaluate(typed))
        }
    }

    @Test fun emptyContinues() {
        for (typed in listOf(null, "", "   ", "\t")) {
            assertEquals(Result.EMPTY, SableQuickCheck.evaluate(typed))
        }
    }

    @Test fun anythingElseIsAMismatch() {
        for (typed in listOf("A", "A7", "A7@@", "Az@", "A7#", "7A@", "B7@", "A72")) {
            assertEquals(typed, Result.MISMATCH, SableQuickCheck.evaluate(typed))
        }
    }

    @Test fun fullTestOnlyWhenTheCheckDidNotPass() {
        assertFalse(SableQuickCheck.showFullTest(true))
        assertTrue(SableQuickCheck.showFullTest(false))
    }
}
