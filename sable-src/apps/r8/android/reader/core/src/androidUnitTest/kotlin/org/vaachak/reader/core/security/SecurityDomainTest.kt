package org.vaachak.reader.core.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.vaachak.reader.core.utils.SecurityUtils

class SecurityDomainTest {

    private lateinit var cryptoManager: CryptoManager

    // Standardized test credentials for PBKDF2 derivation
    private val testPass = "test_password"
    private val testUser = "test_user"

    @Before
    fun setup() {
        java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        cryptoManager = CryptoManager()
    }

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
        assertNotNull(emptyHash)
        assertNotNull(blankHash)
        assertNotEquals(emptyHash, blankHash)
    }

    // --- CryptoManager Tests ---

    @Test
    fun `encrypt and decrypt are perfectly symmetrical`() {
        // Given a simulated Book or Settings JSON payload
        val originalJsonPayload = """
            {
                "userId": "piyush",
                "settings": { "eink": true, "offline": false },
                "secrets": ["key1", "key2"]
            }
        """.trimIndent()

        // When: We encrypt the payload using test credentials
        val encryptedPayload = cryptoManager.encrypt(originalJsonPayload, testPass, testUser)

        // Then: We decrypt the payload back to plain text
        val decryptedString = cryptoManager.decrypt(encryptedPayload, testPass, testUser)

        // Assert: The decrypted text matches the original perfectly
        assertEquals(originalJsonPayload, decryptedString)
    }

    @Test
    fun `encrypting the same payload generates different ciphertexts (GCM nonce uniqueness)`() {
        // Note: A secure AES-256-GCM implementation should use a unique Initialization Vector (IV)
        // for every encryption, meaning encrypting the same text twice yields different payloads.
        val payload = "SuperSecretData"

        val encryption1 = cryptoManager.encrypt(payload, testPass, testUser)
        val encryption2 = cryptoManager.encrypt(payload, testPass, testUser)

        // Assuming EncryptedPayload is a data class or has a proper toString/equals implementation
        assertNotEquals("Secure GCM should use unique IVs/nonces", encryption1, encryption2)

        // But both should still decrypt to the exact same string
        assertEquals(payload, cryptoManager.decrypt(encryption1, testPass, testUser))
        assertEquals(payload, cryptoManager.decrypt(encryption2, testPass, testUser))
    }
}