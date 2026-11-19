// SableOS: one long-press shortcut per mail account on the Sable Mail icon, each opening that account's inbox.
package com.fsck.k9.activity

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

object SableAccountShortcuts {
    private var published: List<Pair<String, String>> = emptyList()

    /** The accounts to publish: those with a label, at most [max] of them, in the order given. */
    fun limit(
        accounts: List<Pair<String, String>>,
        max: Int,
    ): List<Pair<String, String>> = accounts.filter { it.second.isNotBlank() }.take(max.coerceAtLeast(0))

    /** Publishes (uuid, label) pairs as the app's dynamic shortcuts; does nothing when they are unchanged. */
    @Synchronized
    fun publish(
        context: Context,
        accounts: List<Pair<String, String>>,
    ) {
        val wanted = limit(accounts, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        if (wanted == published) return
        val icon = IconCompat.createWithResource(context, context.applicationInfo.icon)
        val shortcuts =
            wanted.map { (uuid, label) ->
                ShortcutInfoCompat
                    .Builder(context, uuid)
                    .setShortLabel(label)
                    .setIcon(icon)
                    .setIntent(MessageHomeActivity.shortcutIntentForAccount(context, uuid))
                    .build()
            }
        if (runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }.getOrDefault(false)) {
            published = wanted
        }
    }
}
