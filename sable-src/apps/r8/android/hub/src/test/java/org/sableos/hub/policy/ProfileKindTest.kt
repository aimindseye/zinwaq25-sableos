package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileKindTest {
    @Test
    fun profilesAreClassifiedAndBadged() {
        assertEquals(ProfileKind.Personal, ProfileKind.fromUserType(ProfileKind.USER_TYPE_PROFILE_MANAGED, true))
        assertEquals(ProfileKind.Work, ProfileKind.fromUserType(ProfileKind.USER_TYPE_PROFILE_MANAGED, false))
        assertEquals(ProfileKind.Private, ProfileKind.fromUserType(ProfileKind.USER_TYPE_PROFILE_PRIVATE, false))
        assertEquals(ProfileKind.Other, ProfileKind.fromUserType(null, false))
        assertEquals(null, ProfileKind.Personal.badge())
        ProfileKind.entries.filter { it != ProfileKind.Personal }.forEach { assertTrue(it.badge() != null) }
    }
}
