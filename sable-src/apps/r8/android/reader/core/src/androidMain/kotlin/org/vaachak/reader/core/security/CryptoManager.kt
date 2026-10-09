package org.vaachak.reader.core.security

import java.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual class CryptoManager actual constructor() {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATION_COUNT = 65536
        private const val KEY_LENGTH = 256
        private const val TAG_LENGTH_BIT = 128
        private const val IV_LENGTH_BYTE = 12

        // Derives the exact same AES key on any device as long as the password and username match
        private fun deriveKey(password: String, saltString: String): SecretKey {
            val salt = saltString.toByteArray(Charsets.UTF_8)
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
            val secretKeyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(secretKeyBytes, "AES")
        }
    }

    actual fun encrypt(plainTextJson: String, secretPass: String, usernameSalt: String): EncryptedPayload {
        val key = deriveKey(secretPass, usernameSalt)
        val cipher = Cipher.getInstance(ALGORITHM)

        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertextBytes = cipher.doFinal(plainTextJson.toByteArray(Charsets.UTF_8))

        return EncryptedPayload(
            ciphertext = Base64.getEncoder().encodeToString(ciphertextBytes),
            iv = Base64.getEncoder().encodeToString(iv)
        )
    }

    actual fun decrypt(payload: EncryptedPayload, secretPass: String, usernameSalt: String): String {
        val key = deriveKey(secretPass, usernameSalt)
        val cipher = Cipher.getInstance(ALGORITHM)

        val ivBytes = Base64.getDecoder().decode(payload.iv)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, ivBytes)

        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val ciphertextBytes = Base64.getDecoder().decode(payload.ciphertext)
        val decryptedBytes = cipher.doFinal(ciphertextBytes)

        return String(decryptedBytes, Charsets.UTF_8)
    }
}