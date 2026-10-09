package org.vaachak.reader.core.security

import org.junit.Assert.*
import org.junit.Test
import org.vaachak.reader.core.utils.SecurityUtils

class SecurityDomainTest {

    // --- SecurityUtils Tests ---

    @Test
    fun `hashPin generates consistent deterministic output for the same input`() {
        val pin = "1234"
        val hash1 = SecurityUtils.hashPin(pin)
        val hash2 = SecurityUtils.hashPin(pin)

        // Then: The hashes must match perfectly
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashPin generates unique hashes for different inputs to prevent collisions`() {
        val hash1 = SecurityUtils.hashPin("1234")
        val hash2 = SecurityUtils.hashPin("5678")
        val hash3 = SecurityUtils.hashPin("12345") // Edge case: substring

        // Then: No two different PINs should result in the same hash
        assertNotEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
    }

    @Test
    fun `hashPin handles empty or blank strings without crashing`() {
        // Given an empty or blank PIN edge case
        val emptyHash = SecurityUtils.hashPin("")
        val blankHash = SecurityUtils.hashPin("   ")

        // Then: It should successfully hash them, and they should be different
        // hashPin returns a non-null String, so "not null" proves nothing; a SHA-256 hex digest is 64 characters.
        assertEquals(SHA256_HEX_LENGTH, emptyHash.length)
        assertEquals(SHA256_HEX_LENGTH, blankHash.length)
        assertNotEquals(emptyHash, blankHash)
    }

    private companion object {
        const val SHA256_HEX_LENGTH = 64
    }
}
