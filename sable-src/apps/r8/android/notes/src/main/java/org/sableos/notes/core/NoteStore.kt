package org.sableos.notes.core

import java.io.File
import java.util.UUID

/** Notes as files in an app-private directory, one `<id>.note` file each ([NoteCodec]). */
class NoteStore(
    private val dir: File,
) {
    /** Every readable note, latest edit first. */
    fun list(): List<Note> =
        dir
            .listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { NoteCodec.decode(file.name.removeSuffix(SUFFIX), file.readText()) }.getOrNull()
            }.sortedByDescending { it.updatedAt }

    /** Writes to a temporary file and renames it, so a crash never leaves half a note. */
    fun save(note: Note) {
        dir.mkdirs()
        val tmp = File(dir, note.id + ".tmp")
        tmp.writeText(NoteCodec.encode(note))
        if (!tmp.renameTo(file(note.id))) {
            tmp.delete()
            error("could not save note ${note.id}")
        }
    }

    fun delete(id: String) {
        file(id).delete()
    }

    private fun file(id: String): File {
        require(ID.matches(id)) { "bad note id" }
        return File(dir, id + SUFFIX)
    }

    companion object {
        private const val SUFFIX = ".note"
        private val ID = Regex("[0-9a-f-]{36}")

        fun newId(): String = UUID.randomUUID().toString()
    }
}
