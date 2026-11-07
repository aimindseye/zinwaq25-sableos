package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyScoreTest {
    private fun check(id: PrivacyCheckId, state: CheckState) = PrivacyCheck(id, state, "")

    private fun all(
        state: CheckState,
        vararg except: Pair<PrivacyCheckId, CheckState>
    ): List<PrivacyCheck> =
        PrivacyCheckId.entries.map { id -> check(id, except.toMap()[id] ?: state) }

    @Test
    fun allGoodIsGood() {
        assertEquals(PrivacyRating.GOOD, PrivacyScore.rate(all(CheckState.GOOD)))
    }

    @Test
    fun unknownIsNeverCountedAgainstThePhone() {
        assertEquals(PrivacyRating.GOOD, PrivacyScore.rate(all(CheckState.UNKNOWN)))
    }

    @Test
    fun aCriticalCheckMakesItPoor() {
        val checks = all(CheckState.GOOD, PrivacyCheckId.SCREEN_LOCK to CheckState.NEEDS_ATTENTION)
        assertEquals(PrivacyRating.POOR, PrivacyScore.rate(checks))
    }

    @Test
    fun oneOrTwoMinorChecksIsFairAndThreeIsPoor() {
        val one = all(CheckState.GOOD, PrivacyCheckId.USB_DEBUGGING to CheckState.NEEDS_ATTENTION)
        assertEquals(PrivacyRating.FAIR, PrivacyScore.rate(one))
        val three =
            all(
                CheckState.GOOD,
                PrivacyCheckId.USB_DEBUGGING to CheckState.NEEDS_ATTENTION,
                PrivacyCheckId.SECURITY_PATCH to CheckState.NEEDS_ATTENTION,
                PrivacyCheckId.INSTALL_APPS to CheckState.NEEDS_ATTENTION
            )
        assertEquals(PrivacyRating.POOR, PrivacyScore.rate(three))
    }

    @Test
    fun theFirstFixIsTheFirstCheckInListOrder() {
        val checks =
            listOf(
                check(PrivacyCheckId.SECURITY_PATCH, CheckState.NEEDS_ATTENTION),
                check(PrivacyCheckId.ENCRYPTION, CheckState.NEEDS_ATTENTION),
                check(PrivacyCheckId.SCREEN_LOCK, CheckState.GOOD)
            )
        assertEquals(PrivacyCheckId.ENCRYPTION, PrivacyScore.nextFix(checks)?.id)
        assertNull(PrivacyScore.nextFix(all(CheckState.GOOD)))
    }

    @Test
    fun thresholds() {
        assertEquals(
            CheckState.GOOD,
            PrivacyScore.sensitiveAppsState(PrivacyScore.SENSITIVE_APPS_LIMIT)
        )
        assertEquals(
            CheckState.NEEDS_ATTENTION,
            PrivacyScore.sensitiveAppsState(PrivacyScore.SENSITIVE_APPS_LIMIT + 1)
        )
        assertEquals(CheckState.UNKNOWN, PrivacyScore.sensitiveAppsState(null))
        assertEquals(CheckState.GOOD, PrivacyScore.installAppsState(0))
        assertEquals(CheckState.NEEDS_ATTENTION, PrivacyScore.installAppsState(2))
        assertEquals(
            CheckState.GOOD,
            PrivacyScore.patchState(PrivacyScore.PATCH_MAX_AGE_DAYS.toLong())
        )
        assertEquals(
            CheckState.NEEDS_ATTENTION,
            PrivacyScore.patchState(PrivacyScore.PATCH_MAX_AGE_DAYS + 1L)
        )
        assertEquals(CheckState.UNKNOWN, PrivacyScore.patchState(null))
    }

    @Test
    fun flagsReadAsGoodWhenOnOrOffAsTheCheckWants() {
        assertEquals(CheckState.GOOD, PrivacyScore.flagState(true, goodWhenOn = true))
        assertEquals(CheckState.NEEDS_ATTENTION, PrivacyScore.flagState(false, goodWhenOn = true))
        assertEquals(CheckState.GOOD, PrivacyScore.flagState(false, goodWhenOn = false))
        assertEquals(CheckState.UNKNOWN, PrivacyScore.flagState(null, goodWhenOn = false))
    }
}
