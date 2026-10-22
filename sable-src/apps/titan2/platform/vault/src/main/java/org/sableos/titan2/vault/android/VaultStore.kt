package org.sableos.titan2.vault.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import org.sableos.titan2.vault.core.FileNames
import org.sableos.titan2.vault.core.ImportOutcome
import org.sableos.titan2.vault.core.Section
import org.sableos.titan2.vault.core.VaultItem

/**
 * Files live in this app's credential-encrypted storage (Context.filesDir) of the private profile, one folder per
 * section. That storage is unreadable while Private Space is locked, so the vault locks with it. Nothing here
 * uses the network, shared storage, MediaStore or backup.
 */
class VaultStore(private val context: Context) {
    private val root: File get() = File(context.filesDir, ROOT_DIR)

    fun dir(section: Section): File = File(root, section.id).apply { mkdirs() }

    fun list(section: Section): List<VaultItem> =
        dir(section).listFiles().orEmpty()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .map { VaultItem(it.name, it.length(), it.lastModified()) }

    /** The stored file for [name], only when it is a direct child of the section folder. */
    fun file(section: Section, name: String): File? {
        val f = File(dir(section), name)
        return f.takeIf { it.isFile && it.parentFile?.canonicalPath == dir(section).canonicalPath }
    }

    /** Copies each document in; the originals are left alone (the caller asks before deleting them). */
    fun import(section: Section, uris: List<Uri>): ImportOutcome {
        val imported = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (uri in uris) {
            val display = displayName(uri)
            val taken = dir(section).list().orEmpty().toSet()
            val name = FileNames.unique(FileNames.sanitize(display), taken)
            val target = File(dir(section), name)
            try {
                copyIn(uri, target)
                imported += name
            } catch (e: IOException) {
                failed += (display ?: name) + " (" + (e.message ?: "read error") + discard(target) + ")"
            } catch (e: IllegalStateException) {
                failed += (display ?: name) + " (" + (e.message ?: "unreadable") + discard(target) + ")"
            } catch (_: SecurityException) {
                failed += (display ?: name) + " (no access" + discard(target) + ")"
            }
        }
        return ImportOutcome(imported, failed)
    }

    /** Removes a half-copied file; says so when it could not, so the user can delete it. */
    private fun discard(target: File): String =
        if (target.delete() || !target.exists()) "" else "; partial copy left"

    private fun copyIn(uri: Uri, target: File) {
        val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "no stream" }
        input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
    }

    /** Writes a copy of [name] to [target], a document the user just created in the system picker. */
    fun export(section: Section, name: String, target: Uri): Boolean = runCatching {
        val src = checkNotNull(file(section, name)) { "missing" }
        val out = checkNotNull(context.contentResolver.openOutputStream(target, "wt")) { "no stream" }
        out.use { o -> src.inputStream().use { it.copyTo(o) } }
        true
    }.getOrDefault(false)

    fun delete(section: Section, name: String): Boolean = file(section, name)?.delete() == true

    /** Deletes picked originals the provider allows deleting; returns how many were removed. */
    fun deleteOriginals(uris: List<Uri>): Int = uris.count { uri ->
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        const val ROOT_DIR = "vault"
    }
}
