package org.sableos.reader.model

/** A user-defined group of library items. Membership is by item id, independent of publication kind. */
data class Collection(
    val id: String,
    val profileId: String,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int = 0,
    val itemIds: Set<String> = emptySet(),
) {
    fun contains(itemId: String): Boolean = itemId in itemIds
}
