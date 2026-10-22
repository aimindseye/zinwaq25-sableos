package org.sableos.titan2.vault.core

/**
 * Secure Vault (PRIVATE_SPACE_DUAL_APPS_WORK_PROFILE_UX, "Secure Vault"; artifact
 * sable-private-space-dual-work-titan2.svg panel 1). Pure Kotlin: no Android imports.
 *
 * The vault is the file layer of Private Space. The app is installed only in the private profile and keeps its
 * files in its own credential-encrypted storage there, so the files lock when Private Space locks.
 * Import copies in; Export and Open each need an explicit confirmation; nothing leaves silently.
 */
enum class Section(val id: String, val title: String, val key: Char, val mimeTypes: List<String>) {
    Photos("photos", "Photos", 'p', listOf("image/*", "video/*")),
    Docs("docs", "Documents", 'd', listOf("*/*")),
    Recordings("recordings", "Recordings", 'r', listOf("audio/*"));

    fun next(dir: Int): Section = entries[(ordinal + dir + entries.size) % entries.size]

    companion object {
        fun fromId(id: String?): Section = entries.firstOrNull { it.id == id } ?: Photos
    }
}

/** One stored file, as listed. */
data class VaultItem(val name: String, val sizeBytes: Long, val modifiedMillis: Long)

object FileNames {
    private const val MAX_LENGTH = 120
    private const val FALLBACK = "file"

    /**
     * A safe single path segment from a provider's display name: no separators, no control characters, no
     * leading dots (no hidden files, no "." or ".."), bounded length with the extension kept.
     */
    fun sanitize(displayName: String?): String {
        val cleaned = displayName.orEmpty()
            .map { c -> if (isUnsafe(c)) '_' else c }
            .joinToString("")
            .trim()
            .trimStart('.')
            .trim()
        return when {
            cleaned.isEmpty() -> FALLBACK

            cleaned.length <= MAX_LENGTH -> cleaned

            else -> {
                val dot = cleaned.lastIndexOf('.')
                val ext = if (dot > 0 &&
                    cleaned.length - dot <= MAX_EXTENSION
                ) {
                    cleaned.substring(dot)
                } else {
                    ""
                }
                cleaned.take(MAX_LENGTH - ext.length) + ext
            }
        }
    }

    /** Path separators, the drive/stream colon and control characters never reach a file name. */
    private fun isUnsafe(c: Char): Boolean = c in UNSAFE || c.isISOControl()

    /** [name], or "base (2).ext", "base (3).ext"... when [taken] already has it. Never overwrites. */
    fun unique(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while ("$base ($n)$ext" in taken) n++
        return "$base ($n)$ext"
    }

    private const val MAX_EXTENSION = 10
    private const val UNSAFE = "/\\:"
}

/** What a key does on the vault list. Letters are plain letters on the Q25 (no Ctrl, no Fn). */
enum class VaultAction {
    Up,
    Down,
    PrevSection,
    NextSection,
    Photos,
    Docs,
    Recordings,
    Open,
    Import,
    Export,
    Delete,
    Lock,
    Leave
}

/** What a key does while a confirmation is showing. */
enum class ConfirmAction { Confirm, Cancel }

object VaultKeys {
    fun action(key: Key): VaultAction? = when (key) {
        Key.Up -> VaultAction.Up
        Key.Down -> VaultAction.Down
        Key.Left -> VaultAction.PrevSection
        Key.Right -> VaultAction.NextSection
        Key.Enter -> VaultAction.Open
        Key.Delete -> VaultAction.Delete
        Key.Back -> VaultAction.Leave
        is Key.Letter -> letter(key.c.lowercaseChar())
        Key.Other -> null
    }

    private fun letter(c: Char): VaultAction? = when (c) {
        Section.Photos.key -> VaultAction.Photos
        Section.Docs.key -> VaultAction.Docs
        Section.Recordings.key -> VaultAction.Recordings
        'i' -> VaultAction.Import
        'e' -> VaultAction.Export
        'x' -> VaultAction.Delete
        'l' -> VaultAction.Lock
        else -> null
    }

    /** Enter or Y confirms; Back, Esc or N cancels; anything else does nothing (no accidental confirm). */
    fun confirm(key: Key): ConfirmAction? = when (key) {
        Key.Enter -> ConfirmAction.Confirm

        Key.Back -> ConfirmAction.Cancel

        is Key.Letter -> when (key.c.lowercaseChar()) {
            'y' -> ConfirmAction.Confirm
            'n' -> ConfirmAction.Cancel
            else -> null
        }

        else -> null
    }

    /** Keys as the Android glue reports them. */
    sealed interface Key {
        data object Up : Key
        data object Down : Key
        data object Left : Key
        data object Right : Key
        data object Enter : Key
        data object Delete : Key
        data object Back : Key
        data object Other : Key
        data class Letter(val c: Char) : Key
    }

    const val HINT =
        "↑↓ file · ←→ or P D R section · Enter open · I import · E export · X delete · L lock now"
}

/** A question the user must answer before anything leaves the vault or is removed. */
data class Confirmation(val kind: Kind, val title: String, val detail: String) {
    enum class Kind { Open, Export, Delete, DeleteOriginals }

    companion object {
        fun open(item: VaultItem) = Confirmation(
            Kind.Open,
            "Open ${item.name}?",
            "The app you pick (inside Private Space) can read this one file until it closes it. " +
                "Enter or Y opens · Back or N cancels."
        )

        fun export(item: VaultItem) = Confirmation(
            Kind.Export,
            "Export ${item.name}?",
            "A copy is written where you choose next and is no longer protected by the vault. " +
                "The vault copy stays. Enter or Y continues · Back or N cancels."
        )

        fun delete(item: VaultItem) = Confirmation(
            Kind.Delete,
            "Delete ${item.name} from the vault?",
            "It cannot be recovered. Enter or Y deletes · Back or N cancels."
        )

        fun deleteOriginals(count: Int) = Confirmation(
            Kind.DeleteOriginals,
            "Imported $count file(s) as copies. Delete the originals?",
            "The originals are still where they were. Enter or Y deletes them · Back or N keeps them."
        )
    }
}

/** Import results: copies are never reported as moves. */
data class ImportOutcome(val imported: List<String>, val failed: List<String>) {
    fun message(): String = buildString {
        append("Imported ${imported.size} file(s) as copies.")
        if (failed.isNotEmpty()) {
            append(
                " Could not import: "
            ).append(failed.joinToString(", ")).append('.')
        }
    }
}

object VaultText {
    const val TITLE = "Secure Vault"
    const val SUBTITLE = "Private Space · Photos · Documents · Recordings · import/export explicit"
    const val NOT_PRIVATE =
        "Secure Vault works only inside Private Space. " +
            "Unlock Private Space in Sable Start (All apps) " +
            "and open Secure Vault there. Nothing is stored outside it."
    const val EMPTY = "Nothing here yet. Press I to import a copy of a file."
    const val LOCK_FALLBACK =
        "Could not lock Private Space from here; lock it from Sable Start. The vault closed."

    fun size(bytes: Long): String = when {
        bytes < KIB -> "$bytes B"
        bytes < MIB -> "${bytes / KIB} KB"
        bytes < GIB -> "${bytes / MIB} MB"
        else -> "${bytes / GIB} GB"
    }

    private const val KIB = 1024L
    private const val MIB = KIB * 1024L
    private const val GIB = MIB * 1024L
}
