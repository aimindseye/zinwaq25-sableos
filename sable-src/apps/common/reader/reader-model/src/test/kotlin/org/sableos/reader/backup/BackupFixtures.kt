package org.sableos.reader.backup

internal object BackupFixtures {
    private val kinds = listOf("EPUB", "PDF", "COMIC", "AUDIOBOOK")

    fun hash(i: Int): String = "%032x".format(i.toLong() + 1)

    fun item(i: Int, change: (BackupItem) -> BackupItem = { it }): BackupItem =
        change(BackupItem(hash = hash(i), kind = "EPUB", title = "Title $i", author = "Author ${i % 50}"))

    fun document(
        items: List<BackupItem> = emptyList(),
        change: (BackupDocument) -> BackupDocument = { it },
    ): BackupDocument = change(BackupDocument(createdAt = 1_700_000_000_000L, items = items))

    fun large(items: Int, bookmarksPerItem: Int = 3): BackupDocument {
        val list = List(items) { i ->
            item(i) { it.copy(kind = kinds[i % kinds.size], lastOpenedAt = i.toLong(), favorite = i % 7 == 0) }
        }
        val marks = list.flatMapIndexed { i, item ->
            List(bookmarksPerItem) { b ->
                val locator = "{\"type\":\"page\",\"pageIndex\":$b,\"pageCount\":9}"
                BackupBookmark("bm-$i-$b", item.hash, locator, "Page $b", b.toLong())
            }
        }
        val shelves = List(items / SHELF_SIZE) { s ->
            val members = list.subList(s * SHELF_SIZE, s * SHELF_SIZE + SHELF_SIZE).map { it.hash }
            BackupCollection("shelf-$s", "Shelf $s", s.toLong(), s, members)
        }
        val webtoon = "{\"mode\":\"WEBTOON\"}"
        val settings = list.filter { it.kind == "COMIC" }.map { BackupViewSettings(it.hash, webtoon, 5L) }
        return document(list) { it.copy(bookmarks = marks, collections = shelves, viewSettings = settings) }
    }

    private const val SHELF_SIZE = 20
}
