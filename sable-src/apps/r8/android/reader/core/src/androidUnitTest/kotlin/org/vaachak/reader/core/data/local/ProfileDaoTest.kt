package org.vaachak.reader.core.data.local

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.vaachak.reader.core.domain.model.ProfileEntity


class ProfileDaoTest : BaseDaoTest() {

    private lateinit var dao: ProfileDao

    override fun onSetup() {
        dao = database.profileDao()
    }

    private fun createDummyProfile(
        id: String,
        name: String = "Test User",
        timestamp: Long = 1000L,
        pinHash: String? = null
    ) = ProfileEntity(
        profileId = id,
        name = name,
        isGuest = false,
        pinHash = pinHash,
        avatarColorHex = "#FFFFFF",
        lastActiveTimestamp = timestamp
    )

    @Test
    fun `insertProfile and getAllProfiles sorts by lastActiveTimestamp descending`() = runTest {
        val olderProfile = createDummyProfile(id = "user1", timestamp = 1000L)
        val newerProfile = createDummyProfile(id = "user2", timestamp = 5000L)

        dao.insertProfile(olderProfile)
        dao.insertProfile(newerProfile)

        dao.getAllProfiles().test {
            val profiles = awaitItem()
            assertEquals(2, profiles.size)
            assertEquals("user2", profiles[0].profileId)
            assertEquals("user1", profiles[1].profileId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateLastActive modifies timestamp successfully`() = runTest {
        dao.insertProfile(createDummyProfile(id = "user1", timestamp = 1000L))
        val newTime = 9999L
        dao.updateLastActive("user1", newTime)

        val updatedProfile = dao.getProfileById("user1")
        assertEquals(newTime, updatedProfile?.lastActiveTimestamp)
    }

    @Test
    fun `updatePin successfully changes or removes pinHash`() = runTest {
        dao.insertProfile(createDummyProfile(id = "user1", pinHash = "oldHash"))

        dao.updatePin("user1", "newHash")
        assertEquals("newHash", dao.getProfileById("user1")?.pinHash)

        dao.updatePin("user1", null)
        assertNull(dao.getProfileById("user1")?.pinHash)
    }
}