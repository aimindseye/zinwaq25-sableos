// SableOS: the Hub keeps what it has shown until you delete it, as BlackBerry 10's Hub did
// (owner, 2026-11-12, after a review of JL10's Hub). Part of the SableOS home flavor.
package io.github.aimindseye.tenseven.conversations

import android.content.Context
import android.content.SharedPreferences
import io.github.aimindseye.tenseven.model.HubMessageKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pure policy for the kept Hub: what is kept, how kept items join the live ones, the "every message on
 * its own" rows, the system packages, Priority Hub, and the date sections. Unit tested.
 */
object SableHubKeep {
    const val MAX_KEPT = 1000

    /** Packages whose notifications are Android's own; "Include system notifications" off hides them. */
    val SYSTEM_PACKAGES =
        setOf(
            "android",
            "com.android.systemui",
            "com.android.shell",
            "com.android.settings",
            "com.android.providers.downloads",
            "org.lineageos.setupwizard",
            "org.lineageos.updater",
        )

    /** Ongoing notices (music, calls, USB) come and go with their state; Sable Mail keeps its own mail. */
    fun keepable(item: HubNotification): Boolean = !item.isOngoing && item.packageName != SableMailFeed.MAIL_PACKAGE

    /** [kept] with the keepable [live] items added or updated, newest first, at most [max]. */
    fun remember(
        kept: List<HubNotification>,
        live: List<HubNotification>,
        max: Int = MAX_KEPT,
    ): List<HubNotification> {
        val fresh = live.filter(::keepable)
        if (fresh.isEmpty()) return kept
        val byKey = LinkedHashMap<String, HubNotification>()
        kept.forEach { byKey[it.key] = it }
        fresh.forEach { item ->
            val old = byKey[item.key]
            byKey[item.key] = if (old == null) item else item.copy(messages = mergeMessages(old.messages, item.messages))
        }
        return byKey.values.sortedByDescending(HubNotification::postedAt).take(max)
    }

    /** An app often replaces its notification with only the newest message; keep the earlier ones too. */
    private fun mergeMessages(
        old: List<ConversationMessage>,
        new: List<ConversationMessage>,
    ): List<ConversationMessage> =
        (old + new)
            .distinctBy { Triple(it.timestamp, it.text, it.isOutgoing) }
            .sortedBy(ConversationMessage::timestamp)
            .takeLast(MAX_MESSAGES)

    /**
     * What the Hub shows: [live] items, then the [kept] ones the shade no longer holds. An item deleted at a
     * time stays hidden until its app posts it again later; system packages go when [system] is off.
     */
    fun view(
        live: List<HubNotification>,
        kept: List<HubNotification>,
        deleted: Map<String, Long>,
        keep: Boolean,
        system: Boolean,
    ): List<HubNotification> {
        fun shown(item: HubNotification): Boolean =
            item.postedAt > (deleted[item.key] ?: Long.MIN_VALUE) && (system || item.packageName !in SYSTEM_PACKAGES)
        val liveKeys = live.map(HubNotification::key).toSet()
        val older = if (keep) kept.filter { it.key !in liveKeys } else emptyList()
        return (live + older).filter(::shown).sortedByDescending(HubNotification::postedAt)
    }

    /** "Every message on its own": one row per incoming message, as BBM did, instead of one per chat. */
    fun perMessage(conversations: List<HubConversation>): List<HubConversation> =
        conversations
            .flatMap { c ->
                val incoming = c.messages.filter { !it.isOutgoing && !it.isHistoric }
                if (incoming.size <= 1) {
                    listOf(c)
                } else {
                    incoming.map { m ->
                        HubConversation(
                            id = c.id + ROW_SEPARATOR + m.id,
                            name = c.name,
                            notifications = c.notifications.filter { it.key == m.notificationKey }.ifEmpty { c.notifications },
                            messages = listOf(m),
                        )
                    }
                }
            }.sortedByDescending { it.latestMessage?.timestamp ?: it.latestNotification.postedAt }

    /** The conversation a row belongs to (a per-message row carries its message after [ROW_SEPARATOR]). */
    fun baseId(rowId: String): String = rowId.substringBefore(ROW_SEPARATOR)

    /** Priority Hub: a person or app the user added, or anyone the user has written to. */
    fun isPriority(
        conversation: HubConversation,
        people: Set<String>,
        apps: Set<String>,
    ): Boolean =
        ConversationState.hash(baseId(conversation.id)) in people ||
            conversation.sourcePackages.any { it in apps } ||
            conversation.messages.any(ConversationMessage::isOutgoing)

    /** The time Mark Unread reads up to: just before the newest incoming message, so it counts again. */
    fun unreadMark(conversation: HubConversation): Long? =
        conversation.messages
            .filter { !it.isOutgoing }
            .maxOfOrNull(ConversationMessage::timestamp)
            ?.minus(1)

    data class Section(
        val key: String,
        val label: String,
        val rows: List<HubConversation>,
    )

    /** "Unread" first, then one section per day, newest first: Today, Yesterday, then the date. */
    fun sections(
        rows: List<HubConversation>,
        unread: (HubConversation) -> Int,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<Section> {
        val (fresh, rest) = rows.partition { unread(it) > 0 }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days =
            rest
                .groupBy { Instant.ofEpochMilli(timeOf(it)).atZone(zone).toLocalDate() }
                .entries
                .sortedByDescending { it.key }
                .map { (day, list) -> Section("day:$day", dayLabel(day, today, locale), list) }
        return (if (fresh.isEmpty()) emptyList() else listOf(Section("unread", "Unread", fresh))) + days
    }

    fun dayLabel(
        day: LocalDate,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String =
        when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else ->
                day.format(
                    DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy", locale),
                )
        }

    private fun timeOf(c: HubConversation): Long = c.latestMessage?.timestamp ?: c.latestNotification.postedAt

    /** The line under "Hub": which source, and how many conversations are unread. */
    fun subtitle(
        source: String?,
        unread: Int,
    ): String = (source ?: "All accounts") + if (unread > 0) " · $unread unread" else ""

    data class Source(
        val packageName: String,
        val label: String,
        val unread: Int,
    )

    /** Each app in the Hub with its unread count, for Views: unread apps first, then by name. */
    fun sources(
        rows: List<HubConversation>,
        unread: (HubConversation) -> Int,
    ): List<Source> =
        rows
            .groupBy { it.latestNotification.packageName }
            .map { (pkg, list) -> Source(pkg, list.first().latestNotification.appName, list.sumOf(unread)) }
            .sortedWith(compareByDescending<Source> { it.unread > 0 }.thenBy { it.label.lowercase(Locale.ROOT) })

    /** One kept item per line; every text field Base64, so no separator can appear inside one. */
    fun encode(items: List<HubNotification>): String =
        items.joinToString("\n") { n ->
            listOf(
                n.key,
                n.conversationId,
                n.conversationName,
                n.packageName,
                n.appName,
                n.postedAt.toString(),
                n.kind.name,
                n.messages.joinToString(",") { m -> b64(encodeMessage(m)) },
            ).joinToString("\t") { b64(it) }
        }

    fun decode(text: String): List<HubNotification> =
        text.lineSequence().filter(String::isNotBlank).mapNotNull { line ->
            runCatching {
                val f = line.split('\t').map(::unb64)
                val messages = f[7].split(',').filter(String::isNotEmpty).map { decodeMessage(unb64(it)) }
                HubNotification(
                    key = f[0],
                    conversationId = f[1],
                    conversationName = f[2],
                    packageName = f[3],
                    appName = f[4],
                    postedAt = f[5].toLong(),
                    isOngoing = false,
                    messages = messages,
                    contentIntent = null,
                    replyAction = null,
                    kind = HubMessageKind.valueOf(f[6]),
                )
            }.getOrNull()
        }.toList()

    private fun encodeMessage(m: ConversationMessage): String =
        listOf(
            m.id,
            m.conversationId,
            m.notificationKey,
            m.packageName,
            m.appName,
            m.text,
            m.timestamp.toString(),
            m.senderName.orEmpty(),
            if (m.senderName == null) "0" else "1",
            if (m.isOutgoing) "1" else "0",
            if (m.isHistoric) "1" else "0",
        ).joinToString("\t") { b64(it) }

    private fun decodeMessage(text: String): ConversationMessage {
        val f = text.split('\t').map(::unb64)
        return ConversationMessage(
            id = f[0],
            conversationId = f[1],
            notificationKey = f[2],
            packageName = f[3],
            appName = f[4],
            text = f[5],
            timestamp = f[6].toLong(),
            senderName = if (f[8] == "1") f[7] else null,
            isOutgoing = f[9] == "1",
            isHistoric = f[10] == "1",
        )
    }

    private fun b64(s: String): String = Base64.getEncoder().withoutPadding().encodeToString(s.toByteArray())

    private fun unb64(s: String): String = String(Base64.getDecoder().decode(s))

    const val ROW_SEPARATOR = "\u001F"
    private const val MAX_MESSAGES = 50
}

/** The Hub options SableOS adds (Hub settings > SableOS), kept in the home's own preferences. */
object SableHubOptions {
    private const val PREFS = "sable_hub"
    const val KEY_KEEP = "keep_messages"
    const val KEY_PER_MESSAGE = "every_message_on_its_own"
    const val KEY_SYSTEM = "system_notifications"
    private const val KEY_PRIORITY_PEOPLE = "priority_people"
    private const val KEY_PRIORITY_APPS = "priority_apps"

    @Volatile var keep = true
        private set

    @Volatile var perMessage = false
        private set

    @Volatile var system = true
        private set

    @Volatile var priorityPeople: Set<String> = emptySet()
        private set

    @Volatile var priorityApps: Set<String> = emptySet()
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        keep = p.getBoolean(KEY_KEEP, true)
        perMessage = p.getBoolean(KEY_PER_MESSAGE, false)
        system = p.getBoolean(KEY_SYSTEM, true)
        priorityPeople = p.getStringSet(KEY_PRIORITY_PEOPLE, null).orEmpty().toSet()
        priorityApps = p.getStringSet(KEY_PRIORITY_APPS, null).orEmpty().toSet()
        prefs = p
    }

    fun set(
        context: Context,
        key: String,
        value: Boolean,
    ) {
        init(context)
        when (key) {
            KEY_KEEP -> keep = value
            KEY_PER_MESSAGE -> perMessage = value
            KEY_SYSTEM -> system = value
        }
        prefs?.edit()?.putBoolean(key, value)?.apply()
        if (key == KEY_KEEP && !value) SableHubHistory.clear()
        ConversationHub.notifyChanged()
    }

    fun isPriority(conversation: HubConversation): Boolean = SableHubKeep.isPriority(conversation, priorityPeople, priorityApps)

    fun isPriorityPerson(conversation: HubConversation): Boolean =
        ConversationState.hash(SableHubKeep.baseId(conversation.id)) in priorityPeople

    fun togglePerson(
        context: Context,
        conversation: HubConversation,
    ) {
        init(context)
        val h = ConversationState.hash(SableHubKeep.baseId(conversation.id))
        priorityPeople = if (h in priorityPeople) priorityPeople - h else priorityPeople + h
        prefs?.edit()?.putStringSet(KEY_PRIORITY_PEOPLE, priorityPeople)?.apply()
        ConversationHub.notifyChanged()
    }

    fun toggleApp(
        context: Context,
        packageName: String,
    ) {
        init(context)
        priorityApps = if (packageName in priorityApps) priorityApps - packageName else priorityApps + packageName
        prefs?.edit()?.putStringSet(KEY_PRIORITY_APPS, priorityApps)?.apply()
        ConversationHub.notifyChanged()
    }
}

/**
 * The kept Hub items, in the home's no-backup storage (credential encrypted, never in a backup, gone when the
 * home's data is cleared). Written on a background thread; read once per process.
 */
object SableHubHistory {
    private const val FILE = "sable_hub_kept.txt"
    private const val DELETED_FILE = "sable_hub_deleted.txt"
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: java.io.File? = null

    @Volatile private var kept: List<HubNotification> = emptyList()

    @Volatile private var deleted: Map<String, Long> = emptyMap()

    fun init(context: Context) {
        SableHubOptions.init(context)
        if (dir != null) return
        synchronized(lock) {
            if (dir != null) return
            val d = context.applicationContext.noBackupFilesDir
            kept = runCatching { SableHubKeep.decode(java.io.File(d, FILE).readText()) }.getOrDefault(emptyList())
            deleted =
                runCatching {
                    java.io
                        .File(d, DELETED_FILE)
                        .readLines()
                        .mapNotNull { line ->
                            val at = line.substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
                            line.substringAfter(' ') to at
                        }.toMap()
                }.getOrDefault(emptyMap())
            dir = d
        }
    }

    /** Called with the shade's items each time they change. */
    fun record(live: List<HubNotification>) {
        if (!SableHubOptions.keep || dir == null) return
        val next = synchronized(lock) { SableHubKeep.remember(kept, live).also { kept = it } }
        save(next, deleted)
    }

    fun view(live: List<HubNotification>): List<HubNotification> =
        SableHubKeep.view(live, kept, deleted, SableHubOptions.keep, SableHubOptions.system)

    fun rows(conversations: List<HubConversation>): List<HubConversation> =
        if (SableHubOptions.perMessage) SableHubKeep.perMessage(conversations) else conversations

    fun delete(items: List<HubNotification>) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        val keys = items.map(HubNotification::key).toSet()
        val (k, d) =
            synchronized(lock) {
                kept = kept.filterNot { it.key in keys }
                deleted = (deleted + keys.associateWith { maxOf(now, items.maxOf(HubNotification::postedAt)) }).entries
                    .sortedByDescending { it.value }
                    .take(SableHubKeep.MAX_KEPT)
                    .associate { it.toPair() }
                kept to deleted
            }
        save(k, d)
    }

    fun forget(packages: Set<String>) {
        val next = synchronized(lock) { kept.filterNot { it.packageName in packages }.also { kept = it } }
        save(next, deleted)
    }

    fun clear() {
        synchronized(lock) {
            kept = emptyList()
            deleted = emptyMap()
        }
        save(emptyList(), emptyMap())
    }

    private fun save(
        items: List<HubNotification>,
        gone: Map<String, Long>,
    ) {
        val d = dir ?: return
        writer.execute {
            runCatching {
                writeAtomically(java.io.File(d, FILE), SableHubKeep.encode(items))
                writeAtomically(java.io.File(d, DELETED_FILE), gone.entries.joinToString("\n") { "${it.value} ${it.key}" })
            }
        }
    }

    private fun writeAtomically(
        file: java.io.File,
        text: String,
    ) {
        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            // Rename can fail across a full disk or a stale target: write in place, then drop the copy.
            file.writeText(text)
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }
}
