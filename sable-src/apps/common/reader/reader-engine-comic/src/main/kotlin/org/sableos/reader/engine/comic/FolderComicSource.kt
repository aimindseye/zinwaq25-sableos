package org.sableos.reader.engine.comic

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * A directory of page images on the plain file system (an app-private import or a test fixture). Symbolic links are
 * never followed, depth and entry counts are bounded, and a page path must resolve inside the root.
 */
class FolderComicSource private constructor(
    private val root: File,
    override val limits: ComicLimits,
    override val catalog: ComicCatalog,
    override val info: ComicInfo?,
) : ComicSource {
    override fun readPage(index: Int): ByteArray {
        val page = catalog.pages.getOrNull(index) ?: throw ComicPageException(PageFailure.UNREADABLE, "no page $index")
        ComicCatalogBuilder.checkReadable(page, limits)
        val file = resolveInside(root, page.handle) ?: throw ComicPageException(PageFailure.UNREADABLE, "outside root")
        return try {
            file.inputStream().use { BoundedRead.readAll(it, limits.maxPageBytes) }
        } catch (e: IOException) {
            throw ComicPageException(PageFailure.UNREADABLE, e.message, e)
        }
    }

    override fun close() = Unit

    companion object {
        fun open(root: File, limits: ComicLimits = ComicLimits()): ComicSource {
            if (!root.isDirectory) throw ComicOpenException(ComicOpenFailure.NOT_FOUND)
            val entries = walk(root, limits).asSequence()
            val catalog = ComicCatalogBuilder.build(entries, limits)
            val info = catalog.comicInfoHandle?.let { handle ->
                resolveInside(root, handle)?.takeIf { it.length() <= limits.maxComicInfoBytes }
                    ?.let { runCatching { it.readBytes() }.getOrNull() }
                    ?.let { ComicInfoParser.parse(it, limits) }
            }
            return FolderComicSource(root, limits, catalog, info)
        }

        private fun walk(root: File, limits: ComicLimits): List<RawEntry> = Walker(root, limits).run()

        /** Resolves [relative] under [root] without following links; null if it would leave the root. */
        fun resolveInside(root: File, relative: String): File? {
            val target = File(root, relative)
            val canonicalRoot = root.canonicalFile.toPath()
            val canonicalTarget = target.canonicalFile.toPath()
            val linkSafe = !Files.isSymbolicLink(target.toPath()) &&
                Files.exists(canonicalTarget, LinkOption.NOFOLLOW_LINKS)
            return target.takeIf { linkSafe && canonicalTarget.startsWith(canonicalRoot) }
        }
    }
}

/** Iterative, bounded directory walk that never follows symbolic links. */
private class Walker(private val root: File, private val limits: ComicLimits) {
    private val out = ArrayList<RawEntry>()
    private val pending = ArrayDeque<Pair<File, Int>>()

    fun run(): List<RawEntry> {
        pending.addLast(root to 0)
        while (pending.isNotEmpty()) {
            val (dir, depth) = pending.removeLast()
            for (child in dir.listFiles().orEmpty()) {
                if (out.size > limits.maxEntries) throw ComicOpenException(ComicOpenFailure.TOO_MANY_ENTRIES)
                visit(child, depth)
            }
        }
        return out
    }

    private fun visit(child: File, depth: Int) {
        when {
            Files.isSymbolicLink(child.toPath()) -> Unit
            child.isDirectory -> if (depth < limits.maxFolderDepth) pending.addLast(child to depth + 1)
            else -> {
                val relative = child.relativeTo(root).invariantSeparatorsPath
                out += RawEntry(relative, child.length(), 0L, relative)
            }
        }
    }
}
