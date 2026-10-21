package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubCall
import org.sableos.hub.HubCallKind
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import org.sableos.hub.HubMessage
import org.sableos.hub.HubPerson

/** Hub sub-filters, search, launch extras, counts-only state, row actions and thread keys. */
class HubCommsTest {
    private val maya =
        HubConversation(
            threadId = 7L,
            address = "+15550100100",
            displayName = "Maya",
            lastBody = "Train arrives 7:18",
            lastDateMillis = 5_000L,
            unreadCount = 2,
        )
    private val bank =
        HubConversation(
            threadId = 8L,
            address = "BANK",
            displayName = "BANK",
            lastBody = "Statement ready",
            lastDateMillis = 1_000L,
            unreadCount = 0,
        )
    private val signal =
        HubConversation(
            threadId = -42L,
            address = "team",
            displayName = "Team chat",
            lastBody = "Deploy done",
            lastDateMillis = 3_000L,
            unreadCount = 1,
            source = HubConversationSource.ConnectedApp,
            sourceLabel = "Signal",
            sourcePackage = "org.thoughtcrime.securesms",
            sourceUserSerial = 0L,
            sourceNotificationKey = "k",
            canQuickReply = true,
        )
    private val gmail =
        signal.copy(
            threadId = -43L,
            address = "inbox",
            displayName = "Inbox",
            sourceLabel = "Gmail",
            sourcePackage = "com.google.android.gm",
            canQuickReply = false,
            sourceNotificationKey = null,
        )
    private val missed =
        HubCall(id = 1L, number = "+1 555 010 0200", displayName = "Alex", dateMillis = 4_000L, kind = HubCallKind.Missed)
    private val outgoing =
        HubCall(id = 2L, number = "+15550100100", displayName = "Maya", dateMillis = 2_000L, kind = HubCallKind.Outgoing)
    private val conversations = listOf(maya, bank, signal, gmail)
    private val calls = listOf(missed, outgoing)
    private val people = listOf(HubPerson(1L, "Maya", "+1 (555) 010-0100"), HubPerson(2L, "Alex", "+15550100200"))

    private fun keys(
        filter: HubInboxFilter,
        query: String = "",
        today: Long = 0L,
    ) = HubInbox.items(conversations, calls, filter, query, people, today).map(HubInboxItem::key)

    @Test
    fun priorityKeepsUnreadAnswerableAndMissed() {
        assertEquals(
            listOf("conversation:7:+15550100100", "call:1", "conversation:-42:team", "conversation:-43:inbox"),
            keys(HubInboxFilter.Priority),
        )
    }

    @Test
    fun missedKeepsOnlyCallsNotTaken() {
        assertEquals(listOf("call:1"), keys(HubInboxFilter.Missed))
        assertEquals(1, HubInbox.attentionCount(conversations, calls, HubInboxFilter.Missed))
    }

    @Test
    fun peopleKeepsSmsAndCallsWithAddressBookNumbers() {
        assertEquals(listOf("conversation:7:+15550100100", "call:1", "call:2"), keys(HubInboxFilter.People))
        // Without contacts nothing is a person.
        assertTrue(HubInbox.items(conversations, calls, HubInboxFilter.People).isEmpty())
    }

    @Test
    fun everyFilterHasItsOwnKey() {
        assertEquals(HubInboxFilter.entries.size, HubInboxFilter.entries.map { it.shortcut }.toSet().size)
        assertEquals(HubInboxFilter.Priority, HubInboxFilter.forShortcut('P'))
        assertEquals(HubInboxFilter.People, HubInboxFilter.forShortcut('o'))
        assertNull(HubInboxFilter.forShortcut(HubSourceDock.SHORTCUT))
        assertNull(HubInboxFilter.forShortcut('/'))
    }

    @Test
    fun searchWordsNarrowAndPlainWordsMustAllMatch() {
        assertEquals(
            listOf("conversation:7:+15550100100", "call:1", "conversation:-42:team", "conversation:-43:inbox"),
            keys(HubInboxFilter.All, "unread"),
        )
        assertEquals(listOf("call:1"), keys(HubInboxFilter.All, "missed"))
        assertEquals(listOf("conversation:-42:team"), keys(HubInboxFilter.All, "app:signal"))
        assertEquals(listOf("conversation:7:+15550100100", "call:2"), keys(HubInboxFilter.All, "from:maya"))
        assertEquals(listOf("conversation:7:+15550100100"), keys(HubInboxFilter.All, "train 7:18"))
        assertTrue(keys(HubInboxFilter.All, "train deploy").isEmpty())
        assertEquals(listOf("conversation:7:+15550100100", "call:1"), keys(HubInboxFilter.All, "today", today = 4_000L))
        assertTrue(HubSearch.parse("  ").isEmpty)
        assertEquals(listOf("from:"), HubSearch.parse("from:").words)
    }

    @Test
    fun launchFilterAcceptsNamesAndAliasesOnly() {
        assertEquals(HubInboxFilter.Priority, HubIntents.launchFilter(" Priority "))
        assertEquals(HubInboxFilter.Messages, HubIntents.launchFilter("msgs"))
        assertEquals(HubInboxFilter.Missed, HubIntents.launchFilter("missed-calls"))
        assertEquals(HubInboxFilter.Calls, HubIntents.launchFilter("recent"))
        assertEquals(HubInboxFilter.People, HubIntents.launchFilter("PEOPLE"))
        assertNull(HubIntents.launchFilter("settings"))
        assertNull(HubIntents.launchFilter(null))
    }

    @Test
    fun launchAddressIsBoundedAndPrintable() {
        assertEquals("+1 555 0100", HubIntents.launchAddress(" +1 555 0100 "))
        assertNull(HubIntents.launchAddress(""))
        assertNull(HubIntents.launchAddress("+++"))
        assertNull(HubIntents.launchAddress("1\n2"))
        assertNull(HubIntents.launchAddress("1".repeat(HubIntents.MAX_ADDRESS_LENGTH + 1)))
    }

    @Test
    fun privacyProviderCallersAreFixed() {
        assertTrue(HubPrivacyCallers.allowed(listOf("com.android.dialer")))
        assertTrue(HubPrivacyCallers.allowed(listOf("x", "com.android.contacts")))
        assertFalse(HubPrivacyCallers.allowed(listOf("com.example.spy")))
        assertFalse(HubPrivacyCallers.allowed(emptyList()))
    }

    @Test
    fun countsOnlySummaryHasCategoriesAndNoNames() {
        val counts = HubPrivateSummary.counts(conversations, calls)
        assertEquals(listOf("Messages", "Mail", "Notifications", "Missed calls"), counts.map { it.label })
        assertEquals(listOf(1, 1, 1, 1), counts.map { it.count })
        assertEquals("1 unread · hidden", counts.first().detail)
        val text = counts.joinToString { it.label + it.detail } + HubPrivateSummary.oneLine(counts)
        listOf("Maya", "Alex", "Signal", "Train").forEach { assertFalse(text.contains(it)) }
        assertEquals("Nothing new", HubPrivateSummary.oneLine(HubPrivateSummary.counts(listOf(bank), emptyList())))
    }

    @Test
    fun privacyStateTitlesAndList() {
        assertFalse(HubPrivacyState().countsOnly)
        val locked = HubPrivacyState(deviceLocked = true, privateMode = true)
        assertTrue(locked.countsOnly)
        assertEquals("Locked", locked.title)
        assertFalse(locked.canShowList)
        val private = HubPrivacyState(privateMode = true)
        assertEquals("Private mode", private.title)
        assertTrue(private.canShowList)
    }

    @Test
    fun privacyBadgeNamesSourceAndState() {
        assertEquals("SMS · Private OK", HubPrivacyBadge.text(maya, HubPrivacyState()))
        assertEquals("SMS · Private mode", HubPrivacyBadge.text(maya, HubPrivacyState(privateMode = true)))
        assertEquals("Signal · Work · Locked", HubPrivacyBadge.text(signal.copy(profileBadge = "Work"), HubPrivacyState(true)))
        assertEquals("Signal · Preview hidden", HubPrivacyBadge.text(signal.copy(lastBody = ""), HubPrivacyState()))
    }

    @Test
    fun rowActionsAreProviderSafe() {
        assertEquals(
            listOf(
                HubRowAction.Open,
                HubRowAction.Reply,
                HubRowAction.CallBack,
                HubRowAction.OpenInMessages,
                HubRowAction.PrivacyOptions,
            ),
            HubRowActions.forConversation(maya),
        )
        // An alphanumeric sender cannot be called.
        assertFalse(HubRowAction.CallBack in HubRowActions.forConversation(bank))
        assertEquals(
            listOf(
                HubRowAction.Open,
                HubRowAction.Reply,
                HubRowAction.OpenSourceApp,
                HubRowAction.SourceNotifications,
                HubRowAction.PrivacyOptions,
            ),
            HubRowActions.forConversation(signal),
        )
        assertFalse(HubRowAction.Reply in HubRowActions.forConversation(gmail))
        val locked = HubPrivacyState(deviceLocked = true)
        assertEquals(listOf(HubRowAction.Open, HubRowAction.PrivacyOptions), HubRowActions.forConversation(maya, locked))
        assertEquals(listOf(HubRowAction.CallBack, HubRowAction.Text, HubRowAction.PrivacyOptions), HubRowActions.forCall(missed))
        assertEquals(listOf(HubRowAction.PrivacyOptions), HubRowActions.forCall(missed.copy(number = "")))
    }

    private fun ctx(
        composer: Boolean = false,
        draftEmpty: Boolean = true,
        atLatest: Boolean = true,
        search: Boolean = false,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ) = HubThreadKeyContext(composer, draftEmpty, atLatest, search, ctrl, alt, shift)

    @Test
    fun threadKeysReturnToLatestAndPage() {
        assertEquals(HubThreadCommand.Latest, HubThreadKeys.decide(HubThreadKey.End, ctx(atLatest = false)))
        assertEquals(HubThreadCommand.Latest, HubThreadKeys.decide(HubThreadKey.End, ctx(composer = true)))
        assertEquals(HubThreadCommand.None, HubThreadKeys.decide(HubThreadKey.End, ctx(composer = true, draftEmpty = false)))
        assertEquals(HubThreadCommand.Latest, HubThreadKeys.decide(HubThreadKey.LetterL, ctx(ctrl = true)))
        assertEquals(HubThreadCommand.None, HubThreadKeys.decide(HubThreadKey.LetterL, ctx()))
        // Esc: out of history or search; at latest it stays Back.
        assertEquals(HubThreadCommand.Latest, HubThreadKeys.decide(HubThreadKey.Escape, ctx(atLatest = false)))
        assertEquals(HubThreadCommand.Latest, HubThreadKeys.decide(HubThreadKey.Escape, ctx(search = true)))
        assertEquals(HubThreadCommand.None, HubThreadKeys.decide(HubThreadKey.Escape, ctx()))
        assertEquals(HubThreadCommand.OlderPage, HubThreadKeys.decide(HubThreadKey.PageUp, ctx(composer = true, draftEmpty = false)))
        assertEquals(HubThreadCommand.NewerPage, HubThreadKeys.decide(HubThreadKey.PageDown, ctx()))
        assertEquals(HubThreadCommand.None, HubThreadKeys.decide(HubThreadKey.PageUp, ctx(alt = true)))
    }

    @Test
    fun slashSearchesUnlessItWouldBeTyped() {
        assertEquals(HubThreadCommand.Search, HubThreadKeys.decide(HubThreadKey.Slash, ctx()))
        assertEquals(HubThreadCommand.Search, HubThreadKeys.decide(HubThreadKey.Slash, ctx(composer = true)))
        assertEquals(HubThreadCommand.None, HubThreadKeys.decide(HubThreadKey.Slash, ctx(composer = true, draftEmpty = false)))
        assertEquals(HubThreadCommand.None, HubThreadKeys.decide(HubThreadKey.Slash, ctx(search = true)))
    }

    @Test
    fun pagesOverlapByOneAndStayInRange() {
        assertEquals(4, HubThreadKeys.pageTarget(firstVisible = 0, visibleCount = 5, count = 20, older = true))
        assertEquals(0, HubThreadKeys.pageTarget(firstVisible = 3, visibleCount = 5, count = 20, older = false))
        assertEquals(19, HubThreadKeys.pageTarget(firstVisible = 18, visibleCount = 5, count = 20, older = true))
        assertEquals(1, HubThreadKeys.pageTarget(firstVisible = 0, visibleCount = 1, count = 3, older = true))
        assertEquals(0, HubThreadKeys.pageTarget(firstVisible = 0, visibleCount = 5, count = 0, older = true))
    }

    private fun message(
        id: Long,
        body: String,
        incoming: Boolean = true,
    ) = HubMessage(id, 7L, "+1", body, id, incoming, read = true)

    @Test
    fun threadSearchStepsThroughMatchesNewestFirst() {
        val newestFirst = listOf(message(3, "see you at 7"), message(2, "Train late"), message(1, "train at 7"))
        val matches = HubThreadSearch.matches(newestFirst, "TRAIN")
        assertEquals(listOf(1, 2), matches)
        assertEquals(0, HubThreadSearch.step(-1, 2, older = true))
        assertEquals(1, HubThreadSearch.step(0, 2, older = true))
        assertEquals(0, HubThreadSearch.step(1, 2, older = true))
        assertEquals(1, HubThreadSearch.step(0, 2, older = false))
        assertEquals(-1, HubThreadSearch.step(0, 0, older = true))
        assertEquals("1 of 2", HubThreadSearch.status(0, 2, "train"))
        assertEquals("No matches", HubThreadSearch.status(-1, 0, "x"))
        assertEquals("", HubThreadSearch.status(-1, 0, " "))
        assertTrue(HubThreadSearch.matches(newestFirst, " ").isEmpty())
    }

    @Test
    fun newMessagePillOnlyForArrivalsWhileInHistory() {
        val newest = message(9, "hi")
        assertTrue(HubNewMessagePill.show(atLatest = false, seenNewestId = 8L, newest = newest))
        assertFalse(HubNewMessagePill.show(atLatest = true, seenNewestId = 8L, newest = newest))
        assertFalse(HubNewMessagePill.show(atLatest = false, seenNewestId = 9L, newest = newest))
        assertFalse(HubNewMessagePill.show(atLatest = false, seenNewestId = 8L, newest = message(9, "me", incoming = false)))
        assertFalse(HubNewMessagePill.show(atLatest = false, seenNewestId = null, newest = newest))
    }
}
