package org.sableos.reader.engine.comic

/** What the engine does with one container entry name. */
enum class EntryVerdict {
    /** A readable image page. */
    IMAGE,

    /** Harmless non-page content (directories, metadata, OS junk, nested archives): never read as a page. */
    IGNORED,

    /** A name that tries to escape or confuse path handling; the entry is never opened. */
    UNSAFE,
}

/** Classifies container entry names. Pure string logic, shared by archives, folders and the Android tree source. */
object ComicEntryPolicy {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val junkNames = setOf(".ds_store", "thumbs.db", "desktop.ini")
    private const val MAC_RESOURCE_DIR = "__macosx"
    private const val MAC_RESOURCE_PREFIX = "._"
    private const val ASCII_CONTROL_END = 0x20
    private const val ASCII_DELETE = 0x7f
    private const val DRIVE_LETTER_COLON_INDEX = 1

    fun classify(rawName: String, limits: ComicLimits = ComicLimits()): EntryVerdict {
        val name = normalize(rawName)
        return when {
            rawName.length > limits.maxNameLength -> EntryVerdict.UNSAFE
            isUnsafe(rawName, name) -> EntryVerdict.UNSAFE
            name.endsWith("/") || name.isEmpty() -> EntryVerdict.IGNORED
            isJunk(name) -> EntryVerdict.IGNORED
            extension(name) in imageExtensions -> EntryVerdict.IMAGE
            else -> EntryVerdict.IGNORED
        }
    }

    /** Container paths use `/`; some Windows tools write `\`. Both mean a separator. */
    fun normalize(rawName: String): String = rawName.replace('\\', '/')

    fun extension(normalizedName: String): String =
        normalizedName.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    fun baseName(normalizedName: String): String = normalizedName.substringAfterLast('/')

    fun parentPath(normalizedName: String): String = normalizedName.substringBeforeLast('/', "")

    private fun isUnsafe(rawName: String, name: String): Boolean =
        rawName.any { it.code < ASCII_CONTROL_END || it.code == ASCII_DELETE } ||
            name.startsWith("/") ||
            hasDriveLetter(name) ||
            name.split('/').any { it == ".." }

    private fun hasDriveLetter(name: String): Boolean =
        name.length > DRIVE_LETTER_COLON_INDEX && name[DRIVE_LETTER_COLON_INDEX] == ':' && name[0].isLetter()

    private fun isJunk(name: String): Boolean {
        val segments = name.split('/')
        val base = segments.last().lowercase()
        return segments.any { it.lowercase() == MAC_RESOURCE_DIR } ||
            base.startsWith(MAC_RESOURCE_PREFIX) ||
            base in junkNames
    }
}
