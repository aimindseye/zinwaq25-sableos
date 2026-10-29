package org.sableos.titan2.camera.core

import org.sableos.titan2.camera.core.CameraText.t

/** What a saved file is, from its MIME type. Decides the share type and the review placeholder. */
enum class CaptureKind(private val labelKey: String) {
    Photo("photo"),
    Raw("raw"),
    Video("video_2");

    val label: String get() = t(labelKey)

    companion object {
        fun ofMime(mime: String): CaptureKind = when {
            mime.startsWith("video/") -> Video
            mime == "image/x-adobe-dng" -> Raw
            else -> Photo
        }
    }
}

/**
 * One file this camera saved in this session. [ref] is the MediaStore Uri on the device; the core
 * never looks inside it, so tests use plain strings.
 */
data class Capture<T>(val ref: T, val name: String, val mime: String) {
    val kind: CaptureKind get() = CaptureKind.ofMime(mime)
}

/** Keys and touch buttons of the quick review. Nothing here acts without an explicit press. */
enum class ReviewAction {
    Older,
    Newer,
    Delete,
    Share,
    ShowInFiles,
    Open,
    Close,

    /** Only in the delete confirmation: delete for real, or keep the file. */
    Confirm,
    Cancel
}

/**
 * Quick review state. [captures] are oldest first and hold only this session's saves; [index]
 * points at the one shown. [confirmingDelete] is the explicit second step before a delete.
 */
data class ReviewState<T>(
    val captures: List<Capture<T>> = emptyList(),
    val open: Boolean = false,
    val index: Int = -1,
    val confirmingDelete: Boolean = false
) {
    val latest: Capture<T>? get() = captures.lastOrNull()
    val current: Capture<T>? get() = if (open) captures.getOrNull(index) else null

    /** "2 / 5", counted from the oldest capture of the session. */
    val position: String get() = if (current == null) "" else "${index + 1} / ${captures.size}"
}

/** Work the activity must do for a review step; the core only decides, it never touches files. */
sealed interface ReviewEffect<out T> {
    data class Delete<T>(val capture: Capture<T>) : ReviewEffect<T>
    data class Share<T>(val capture: Capture<T>) : ReviewEffect<T>
    data class ShowInFiles<T>(val capture: Capture<T>) : ReviewEffect<T>
    data class Open<T>(val capture: Capture<T>) : ReviewEffect<T>
}

data class ReviewStep<T>(
    val state: ReviewState<T>,
    val effect: ReviewEffect<T>? = null,
    val status: String? = null
)

object QuickReview {
    /** The session list is bounded so a long shoot cannot grow it without limit. */
    const val MAX_SESSION_CAPTURES = 200

    /** Remember a new save. An open review keeps showing what it showed. */
    fun <T> added(s: ReviewState<T>, c: Capture<T>): ReviewState<T> {
        val all = (s.captures + c).takeLast(MAX_SESSION_CAPTURES)
        val dropped = s.captures.size + 1 - all.size
        return s.copy(
            captures = all,
            index = if (s.open) (s.index - dropped).coerceAtLeast(0) else all.lastIndex
        )
    }

    /** G or the thumbnail: show the latest capture, or say there is none yet. */
    fun <T> open(s: ReviewState<T>): ReviewStep<T> = if (s.captures.isEmpty()) {
        ReviewStep(s, status = t("nothing_captured_yet_in_this"))
    } else {
        ReviewStep(s.copy(open = true, index = s.captures.lastIndex, confirmingDelete = false))
    }

    fun <T> close(s: ReviewState<T>): ReviewState<T> =
        s.copy(open = false, confirmingDelete = false)

    /**
     * One review action. While the delete confirmation shows, only [ReviewAction.Confirm] and
     * [ReviewAction.Cancel] (and Close, which cancels) do anything, so a stray key can neither
     * delete nor leave the question behind.
     */
    fun <T> apply(s: ReviewState<T>, a: ReviewAction): ReviewStep<T> {
        val c = s.current ?: return ReviewStep(close(s))
        return if (s.confirmingDelete) confirming(s, c, a) else browsing(s, c, a)
    }

    private fun <T> confirming(s: ReviewState<T>, c: Capture<T>, a: ReviewAction): ReviewStep<T> =
        when (a) {
            ReviewAction.Confirm ->
                ReviewStep(s.copy(confirmingDelete = false), ReviewEffect.Delete(c))

            ReviewAction.Cancel, ReviewAction.Close ->
                ReviewStep(s.copy(confirmingDelete = false), status = t("kept", c.name))

            else -> ReviewStep(s, status = confirmText(c))
        }

    private fun <T> browsing(s: ReviewState<T>, c: Capture<T>, a: ReviewAction): ReviewStep<T> =
        when (a) {
            ReviewAction.Older -> step(s, -1, t("oldest_capture_of_this_session"))

            ReviewAction.Newer -> step(s, 1, t("latest_capture"))

            ReviewAction.Delete ->
                ReviewStep(s.copy(confirmingDelete = true), status = confirmText(c))

            ReviewAction.Share -> ReviewStep(s, ReviewEffect.Share(c))

            ReviewAction.ShowInFiles -> ReviewStep(s, ReviewEffect.ShowInFiles(c))

            ReviewAction.Open -> ReviewStep(s, ReviewEffect.Open(c))

            ReviewAction.Close -> ReviewStep(close(s))

            ReviewAction.Confirm, ReviewAction.Cancel -> ReviewStep(s)
        }

    private fun <T> step(s: ReviewState<T>, dir: Int, edge: String): ReviewStep<T> {
        val next = s.index + dir
        return if (next in s.captures.indices) {
            ReviewStep(s.copy(index = next))
        } else {
            ReviewStep(s, status = edge)
        }
    }

    fun confirmText(c: Capture<*>): String = t("delete_enter_or_y_deletes", c.name)

    /**
     * After the file is gone: drop it and show its newer neighbour (or the new latest). The
     * review closes when nothing is left.
     */
    fun <T> removed(s: ReviewState<T>, ref: T): ReviewState<T> {
        val at = s.captures.indexOfFirst { it.ref == ref }
        if (at < 0) return s
        val rest = s.captures.filterIndexed { i, _ -> i != at }
        val index = when {
            rest.isEmpty() -> -1
            s.index > at -> s.index - 1
            else -> s.index.coerceAtMost(rest.lastIndex)
        }
        return s.copy(
            captures = rest,
            index = index,
            open = s.open && rest.isNotEmpty(),
            confirmingDelete = false
        )
    }
}
