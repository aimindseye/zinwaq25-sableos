package org.sableos.start.model

import org.sableos.start.live.AppUnreadCount

/** Hub page tabs: "All · Messages · Calls · Contacts". */
enum class HubFilter(
    val label: String,
) {
    All("All"),
    Messages("Messages"),
    Calls("Calls"),
    Contacts("Contacts"),
    ;

    companion object {
        /** A tab by name in any case ("contacts"), or null. */
        fun fromName(name: String?): HubFilter? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

/**
 * The unread bar at the top of Start's Hub page (after the Ripe launcher's BB7 style: each app
 * with unread notifications as a small icon and its count, then "+N" for the rest). Counts come
 * from Sable Hub's notification listener (`content://org.sableos.hub.snapshot/apps`, package and
 * count only). Pure policy, unit tested in apps/r8/android/sablestart-presentation-check.
 */
object HubBar {
    /** Hub's one counts-only row while the device is locked or Sable private mode is on. */
    const val REDACTED = "*"
    const val MESSAGES_PACKAGE = "org.sableos.messages"
    const val DIALER_PACKAGE = "com.android.dialer"
    const val CONTACTS_PACKAGE = "com.android.contacts"

    /** One bar item: icon, count and focus padding, laid out in a fixed width. */
    const val ITEM_DP = 56
    const val GAP_DP = 6
    const val MAX_COUNT = 99

    /** What Enter on a bar item does. */
    sealed interface Target {
        /** Show this Hub page tab (Sable Messages, Phone, Contacts). */
        data class Tab(
            val filter: HubFilter,
        ) : Target

        /** Open the app. */
        object OpenApp : Target

        /** The counts-only total: open Hub's settings, where private mode is switched. */
        object Private : Target
    }

    data class Item(
        val packageName: String,
        val count: Int,
    ) {
        val redacted: Boolean get() = packageName == REDACTED
    }

    data class Layout(
        val visible: List<Item>,
        /** Apps not shown ("+N"); 0 when every item fits. */
        val overflow: Int,
    )

    /**
     * The bar's items: one per package with a count above 0 that Start can open ([canOpen]; the
     * counts-only total always stays), most unread first, then by package.
     */
    fun items(
        rows: List<AppUnreadCount>,
        canOpen: (String) -> Boolean,
    ): List<Item> =
        rows
            .filter { it.count > 0 }
            .groupBy { it.packageName }
            .map { (pkg, same) -> Item(pkg, same.sumOf { it.count }) }
            .filter { it.redacted || canOpen(it.packageName) }
            .sortedWith(compareByDescending<Item> { it.count }.thenBy { it.packageName })

    /** How many items fit in [widthDp]; at least one. */
    fun slots(
        widthDp: Int,
        itemDp: Int = ITEM_DP,
        gapDp: Int = GAP_DP,
    ): Int = maxOf(1, (widthDp + gapDp) / (itemDp + gapDp))

    /** Every item when they fit in [slots]; otherwise the first slots-1 and "+N" in the last slot. */
    fun layout(
        items: List<Item>,
        slots: Int,
    ): Layout {
        if (items.size <= slots) return Layout(items, 0)
        val shown = items.take(maxOf(0, slots - 1))
        return Layout(shown, items.size - shown.size)
    }

    fun target(packageName: String): Target =
        when (packageName) {
            REDACTED -> Target.Private
            MESSAGES_PACKAGE -> Target.Tab(HubFilter.Messages)
            DIALER_PACKAGE -> Target.Tab(HubFilter.Calls)
            CONTACTS_PACKAGE -> Target.Tab(HubFilter.Contacts)
            else -> Target.OpenApp
        }

    /** "7", or "99+" above [MAX_COUNT]. */
    fun countLabel(count: Int): String = if (count > MAX_COUNT) "$MAX_COUNT+" else count.toString()

    fun overflowLabel(overflow: Int): String = "+$overflow"

    /** The Notifications card line: "3 new in 2 apps", "1 new in 1 app", or "nothing new". */
    fun summary(items: List<Item>): String {
        val total = items.sumOf { it.count }
        if (total == 0) return "nothing new"
        if (items.any { it.redacted }) return "$total new · apps hidden"
        val apps = items.size
        return "$total new in $apps " + if (apps == 1) "app" else "apps"
    }
}
