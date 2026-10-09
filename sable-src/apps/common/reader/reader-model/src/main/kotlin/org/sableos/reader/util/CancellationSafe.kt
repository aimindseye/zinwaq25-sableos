package org.sableos.reader.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for code that may suspend: it never swallows a [CancellationException], so a cancelled coroutine keeps
 * unwinding instead of carrying on as if the call had merely failed. Errors (out of memory and the like) are not
 * swallowed either. Any other exception becomes a failed [Result].
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException || it is Error) throw it }
