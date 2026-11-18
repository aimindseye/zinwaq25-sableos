package io.github.aimindseye.tenseven.frames

/**
 * SableOS cover API (2.0 plan, BB10 "Active Frame covers"): an app can say what its own Active Frame shows,
 * a title and a line of text, through the home's `org.sableos.launcher.covers` provider (SableCoverProvider).
 * Covers are kept in memory only, belong to the calling package (the provider reads the caller from Binder,
 * never from the request), are bounded like every other frame summary, are hidden while the phone is
 * locked, and lapse after [MAX_AGE_MS] unless the app sets them again. Pure, unit-tested.
 */
object SableFrameCovers {
    const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val MAX_TITLE = 60

    private val covers = LinkedHashMap<String, AppSummary>()

    @Synchronized
    fun set(
        packageName: String,
        title: String?,
        detail: String?,
        now: Long,
    ): Boolean {
        val text = AppSummaryPolicy.text(detail.orEmpty())
        if (packageName.isBlank() || text.isBlank()) return false
        covers[packageName] =
            AppSummary(
                key = "cover:$packageName",
                title = AppSummaryPolicy.text(title.orEmpty()).take(MAX_TITLE),
                detail = text,
                source = SummarySource.Cover,
                packageName = packageName,
                updatedAt = now,
            )
        return true
    }

    @Synchronized
    fun clear(packageName: String) {
        covers.remove(packageName)
    }

    /** Covers still fresh at [now]; each shows as the frame's summary with the time it was set. */
    @Synchronized
    fun snapshot(now: Long): List<AppSummary> {
        covers.values.removeAll { now - it.updatedAt > MAX_AGE_MS }
        return covers.values.toList()
    }

    @Synchronized
    fun clearAll() = covers.clear()
}
