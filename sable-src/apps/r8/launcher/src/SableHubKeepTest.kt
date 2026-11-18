package io.github.aimindseye.tenseven.conversations

import io.github.aimindseye.tenseven.model.HubMessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private const val CHAT_APP = "app.chat"

class SableHubKeepTest {
    private fun msg(
        id: String,
        at: Long,
        text: String = "hi $id",
        conv: String = "c",
        key: String = "k",
        out: Boolean = false,
    ) = ConversationMessage(id, conv, key, CHAT_APP, "Chat", text, at, "Ann", out)

    private fun note(
        key: String,
        at: Long,
        pkg: String = CHAT_APP,
        conv: String = "c-$key",
        ongoing: Boolean = false,
        messages: List<ConversationMessage> = listOf(msg("m-$key", at, conv = conv, key = key)),
    ) = HubNotification(key, conv, "Ann", pkg, "Chat", at, ongoing, messages, null, null, HubMessageKind.Message)

    @Test
    fun keepsWhatTheShadeDropsUntilDeleted() {
        val kept = SableHubKeep.remember(emptyList(), listOf(note("a", 1), note("b", 2)))
        val shown = SableHubKeep.view(listOf(note("b", 2)), kept, emptyMap(), keep = true, system = true)
        assertEquals(listOf("b", "a"), shown.map { it.key })
        val afterDelete = SableHubKeep.view(listOf(note("b", 2)), kept, mapOf("a" to 5L), keep = true, system = true)
        assertEquals(listOf("b"), afterDelete.map { it.key })
    }

    @Test
    fun aDeletedKeyComesBackWhenTheAppPostsAgainLater() {
        val shown = SableHubKeep.view(listOf(note("a", 9)), emptyList(), mapOf("a" to 5L), keep = true, system = true)
        assertEquals(listOf("a"), shown.map { it.key })
    }

    @Test
    fun keepOffShowsOnlyTheShade() {
        val kept = listOf(note("a", 1))
        assertTrue(SableHubKeep.view(emptyList(), kept, emptyMap(), keep = false, system = true).isEmpty())
    }

    @Test
    fun ongoingAndSableMailAreNotKept() {
        val kept =
            SableHubKeep.remember(
                emptyList(),
                listOf(note("music", 1, ongoing = true), note("mail", 2, pkg = SableMailFeed.MAIL_PACKAGE), note("chat", 3)),
            )
        assertEquals(listOf("chat"), kept.map { it.key })
    }

    @Test
    fun anUpdatedNotificationKeepsItsEarlierMessages() {
        val first = note("a", 1, messages = listOf(msg("1", 1, key = "a")))
        val second = note("a", 2, messages = listOf(msg("2", 2, key = "a")))
        val kept = SableHubKeep.remember(SableHubKeep.remember(emptyList(), listOf(first)), listOf(second))
        assertEquals(listOf("hi 1", "hi 2"), kept.single().messages.map { it.text })
    }

    @Test
    fun keptListIsCapped() {
        val kept = SableHubKeep.remember(emptyList(), (1..20).map { note("n$it", it.toLong()) }, max = 5)
        assertEquals(listOf("n20", "n19", "n18", "n17", "n16"), kept.map { it.key })
    }

    @Test
    fun systemNotificationsCanBeLeftOut() {
        val live = listOf(note("usb", 1, pkg = "android"), note("chat", 2))
        assertEquals(listOf("chat"), SableHubKeep.view(live, emptyList(), emptyMap(), keep = true, system = false).map { it.key })
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val tricky = msg("x", 7, text = "tab\there, line\nbreak, comma, ünïcode", key = "a")
        val items = listOf(note("a", 7, messages = listOf(tricky, msg("y", 8, key = "a", out = true))))
        val back = SableHubKeep.decode(SableHubKeep.encode(items))
        assertEquals(items.single().key, back.single().key)
        assertEquals(items.single().messages, back.single().messages)
        assertNull(back.single().contentIntent)
    }

    @Test
    fun damagedLinesAreSkipped() {
        val good = SableHubKeep.encode(listOf(note("a", 1)))
        assertEquals(1, SableHubKeep.decode("garbage\n$good\n\tx").size)
    }

    @Test
    fun everyMessageOnItsOwnSplitsChats() {
        val c =
            HubConversation(
                "c",
                "Ann",
                listOf(note("k", 3)),
                listOf(msg("1", 1), msg("2", 2), msg("3", 3, out = true)),
            )
        val rows = SableHubKeep.perMessage(listOf(c))
        assertEquals(listOf("2", "1"), rows.map { it.messages.single().id })
        assertEquals("c", SableHubKeep.baseId(rows.first().id))
    }

    @Test
    fun priorityByPersonAppOrReply() {
        val c = HubConversation("c", "Ann", listOf(note("k", 1)), listOf(msg("1", 1)))
        assertFalse(SableHubKeep.isPriority(c, emptySet(), emptySet()))
        assertTrue(SableHubKeep.isPriority(c, setOf(ConversationState.hash("c")), emptySet()))
        assertTrue(SableHubKeep.isPriority(c, emptySet(), setOf(CHAT_APP)))
        val replied = c.copy(messages = c.messages + msg("2", 2, out = true))
        assertTrue(SableHubKeep.isPriority(replied, emptySet(), emptySet()))
    }

    @Test
    fun markUnreadReadsUpToJustBeforeTheNewestIncoming() {
        val c = HubConversation("c", "Ann", listOf(note("k", 5)), listOf(msg("1", 5), msg("2", 9, out = true)))
        assertEquals(4L, SableHubKeep.unreadMark(c))
        val state = ConversationState.EMPTY.markRead("c", SableHubKeep.unreadMark(c)!!)
        assertEquals(1, state.unreadCount(c))
    }

    @Test
    fun sectionsPutUnreadFirstThenDays() {
        val day = 86_400_000L
        val now = 10 * day + 1000
        val rows =
            listOf(
                HubConversation("u", "U", listOf(note("u", now)), listOf(msg("u", now))),
                HubConversation("t", "T", listOf(note("t", now - 10)), listOf(msg("t", now - 10))),
                HubConversation("y", "Y", listOf(note("y", now - day)), listOf(msg("y", now - day))),
                HubConversation("o", "O", listOf(note("o", now - 3 * day)), listOf(msg("o", now - 3 * day))),
            )
        val sections = SableHubKeep.sections(rows, { if (it.id == "u") 1 else 0 }, now, ZoneOffset.UTC, Locale.US)
        assertEquals(listOf("Unread", "Today", "Yesterday", "Thursday, January 8"), sections.map { it.label })
        assertEquals(listOf("u"), sections.first().rows.map { it.id })
    }

    @Test
    fun dayLabelAddsTheYearForAnotherYear() {
        val today = LocalDate.of(2026, 11, 12)
        assertEquals("Friday, December 12, 2025", SableHubKeep.dayLabel(LocalDate.of(2025, 12, 12), today, Locale.US))
    }

    @Test
    fun subtitleAndSources() {
        assertEquals("All accounts · 2 unread", SableHubKeep.subtitle(null, 2))
        assertEquals("Gmail", SableHubKeep.subtitle("Gmail", 0))
        val rows =
            listOf(
                HubConversation("a", "A", listOf(note("a", 1, pkg = "z.app")), listOf(msg("a", 1))),
                HubConversation("b", "B", listOf(note("b", 1, pkg = "a.app")), listOf(msg("b", 1))),
            )
        val sources = SableHubKeep.sources(rows) { if (it.id == "a") 3 else 0 }
        assertEquals(listOf("z.app" to 3, "a.app" to 0), sources.map { it.packageName to it.unread })
    }
}
