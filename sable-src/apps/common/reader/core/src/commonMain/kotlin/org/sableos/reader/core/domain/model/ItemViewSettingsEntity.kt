package org.sableos.reader.core.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.utils.getCurrentTimeMillis

/**
 * Per-title viewer settings (comic reading mode, later audiobook playback preferences). [settingsJson] is owned
 * by the engine that wrote it; a row follows its book and is deleted with it.
 */
@Entity(
    tableName = "item_view_settings",
    primaryKeys = ["bookHash", "profileId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookHash", "profileId"],
            childColumns = ["bookHash", "profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ItemViewSettingsEntity(
    val bookHash: String,
    val profileId: String,
    val settingsJson: String,
    val updatedAt: Long = getCurrentTimeMillis()
)
