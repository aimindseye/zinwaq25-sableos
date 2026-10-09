package org.vaachak.reader.core.domain.model

import kotlinx.serialization.Serializable

enum class DitheringMode { AUTO, ALWAYS_ON, OFF }
enum class CoverAspectRatio { ORIGINAL, UNIFORM }

@Serializable
data class BookshelfPreferences(
    val ditheringMode: DitheringMode = DitheringMode.AUTO,
    val groupBySeries: Boolean = true,

    // NEW: Cover Style Settings (NeoReader style)
    val coverAspectRatio: CoverAspectRatio = CoverAspectRatio.UNIFORM,
    val showFormatBadge: Boolean = true,
    val showFavoriteIcon: Boolean = true,
    val showProgressBadge: Boolean = true,
    val showSyncStatus: Boolean = true
)