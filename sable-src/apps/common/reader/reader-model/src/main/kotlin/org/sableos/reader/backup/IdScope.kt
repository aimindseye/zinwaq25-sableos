package org.sableos.reader.backup

/**
 * Bookmark, highlight and collection ids are database-wide primary keys, so restoring the same backup into two
 * profiles must not make them collide or overwrite each other. A backup stores *canonical* ids; each profile other
 * than the default one stores them with its own prefix. Exporting strips the prefix again, so backup → restore →
 * backup is stable.
 */
object IdScope {
    const val DEFAULT_PROFILE: String = "default"

    fun scoped(profileId: String, canonicalId: String): String =
        if (profileId == DEFAULT_PROFILE) canonicalId else "$profileId:$canonicalId"

    fun canonical(profileId: String, storedId: String): String =
        if (profileId == DEFAULT_PROFILE) storedId else storedId.removePrefix("$profileId:")
}
