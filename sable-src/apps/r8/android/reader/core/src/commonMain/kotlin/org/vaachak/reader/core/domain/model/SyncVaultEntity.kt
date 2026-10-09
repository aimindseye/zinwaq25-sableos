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

package org.vaachak.reader.core.domain.model

import androidx.room.Entity

@Entity(
    tableName = "sync_vault_queue",
    primaryKeys = ["profileId", "entryKey"] // Composite key: Unique per user, per item
)
data class SyncVaultEntity(
    val profileId: String,         // NEW: Required for local Multi-Tenant isolation
    val entryKey: String,          // CHANGED: Replaces bookHash (e.g., "book_123" or "highlight_abc")
    val encryptedBlob: String,     // Your AES-256-GCM ciphertext
    val iv: String,                // Your Initialization Vector
    val remoteUpdatedAt: Long,     // Server timestamp for conflict resolution
    val needsPush: Boolean = false,// True if this device created the blob and needs to upload it
    val isDeleted: Boolean = false // NEW: Tombstone flag for soft-deleting items across devices
)