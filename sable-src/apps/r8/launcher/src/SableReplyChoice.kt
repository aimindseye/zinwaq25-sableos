// SableOS: a merged Hub conversation (one person across SMS, chat and email) replies through the source
// app the user picks, the newest one until they pick. Part of the SableOS home flavor.
package io.github.aimindseye.tenseven.conversations

import java.util.concurrent.ConcurrentHashMap

/** The source app each merged conversation replies through. Held in memory only, like the messages. */
object SableReplyChoice {
    private val chosen = ConcurrentHashMap<String, String>()

    fun packageFor(conversationId: String): String? = chosen[conversationId]

    fun choose(
        conversationId: String,
        packageName: String,
    ) {
        chosen[conversationId] = packageName
    }

    /** The choice among [targets] (newest first): the one for [packageName], else the newest. */
    fun <T> pick(
        targets: List<T>,
        packageName: String?,
        packageOf: (T) -> String,
    ): T? = targets.firstOrNull { packageName != null && packageOf(it) == packageName } ?: targets.firstOrNull()
}
