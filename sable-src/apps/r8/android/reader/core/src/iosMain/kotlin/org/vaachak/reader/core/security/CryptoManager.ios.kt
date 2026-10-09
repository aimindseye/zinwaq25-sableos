/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */

package org.vaachak.reader.core.security

// We expect a Swift class named 'SwiftCryptoBridge' to be compiled into the iOS app.
// This allows us to use modern Apple CryptoKit features safely.
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual class CryptoManager actual constructor() {

    actual fun encrypt(plainTextJson: String, secretPass: String, usernameSalt: String): EncryptedPayload {
        // Here we would call the Swift bridge:
        // val result = SwiftCryptoBridge.encrypt(plainTextJson, secretPass, usernameSalt)
        // return EncryptedPayload(result.ciphertext, result.iv)

        throw NotImplementedError("To support iOS, implement SwiftCryptoBridge using CryptoKit.")
    }

    actual fun decrypt(payload: EncryptedPayload, secretPass: String, usernameSalt: String): String {
        // Here we would call the Swift bridge:
        // return SwiftCryptoBridge.decrypt(payload.ciphertext, payload.iv, secretPass, usernameSalt)

        throw NotImplementedError("To support iOS, implement SwiftCryptoBridge using CryptoKit.")
    }
}
