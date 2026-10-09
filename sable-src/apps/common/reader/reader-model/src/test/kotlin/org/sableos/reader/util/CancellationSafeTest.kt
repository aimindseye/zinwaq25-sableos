package org.sableos.reader.util

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CancellationSafeTest {
    @Test
    fun aValueIsReturnedAsSuccess() {
        assertEquals(7, runCatchingCancellable { 7 }.getOrNull())
    }

    @Test
    fun anOrdinaryExceptionBecomesAFailure() {
        val boom = IOException("boom")
        val result = runCatchingCancellable<Int> { throw boom }
        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun cancellationIsRethrownNotSwallowed() {
        val cancelled = CancellationException("cancelled")
        try {
            runCatchingCancellable<Int> { throw cancelled }
            fail("a CancellationException must propagate")
        } catch (propagated: CancellationException) {
            assertSame(cancelled, propagated)
        }
    }

    @Test
    fun errorsAreNotCaught() {
        val error = StackOverflowError()
        try {
            runCatchingCancellable<Int> { throw error }
            fail("an Error must propagate")
        } catch (propagated: StackOverflowError) {
            assertSame(error, propagated)
        }
    }
}
