package org.vaachak.reader.core.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

actual object SecurityUtils {
    @OptIn(ExperimentalForeignApi::class)
    actual fun hashPin(pin: String): String {
        val bytes = pin.encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)

        // Hooks directly into Apple's native C-based cryptography engine
        bytes.usePinned { input ->
            digest.usePinned { output ->
                CC_SHA256(input.addressOf(0), bytes.size.toUInt(), output.addressOf(0))
            }
        }

        return digest.joinToString("") {
            it.toString(16).padStart(2, '0')
        }
    }
}

