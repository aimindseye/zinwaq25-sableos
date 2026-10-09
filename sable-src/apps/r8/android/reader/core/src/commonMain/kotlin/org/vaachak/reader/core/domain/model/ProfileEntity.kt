package org.vaachak.reader.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.vaachak.reader.core.utils.generateUuid
import org.vaachak.reader.core.utils.getCurrentTimeMillis

@Entity(
    tableName = "reader_profiles",
    // PERFORMANCE: Pre-sorts the timestamps on disk so the UI loads instantly
    indices = [Index(value = ["lastActiveTimestamp"])]
)
data class ProfileEntity(
    @PrimaryKey
    val profileId: String = generateUuid(),
    val name: String,
    val isGuest: Boolean = false,
    val pinHash: String? = null,
    val avatarColorHex: String? = null,
    val lastActiveTimestamp: Long = getCurrentTimeMillis()
)