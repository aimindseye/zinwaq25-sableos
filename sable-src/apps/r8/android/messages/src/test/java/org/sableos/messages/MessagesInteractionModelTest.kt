package org.sableos.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesInteractionModelTest {
    @Test
    fun releaseValidationBlockMatchesPlatformSableContract() {
        val expected =
            linkedMapOf(
                "MESSAGES_CONVERSATION_UX" to "PASS",
                "MESSAGES_CONVERSATION_VISUAL_ARTIFACT_SOURCE_CONTROLLED" to "PASS",
                "TITAN2_MESSAGES_VISUAL_TARGET" to "PASS",
                "ANDROID_MESSAGES_MENTAL_MODEL" to "PASS",
                "KEYBOARD_FIRST_COMPOSITION" to "PASS",
                "PHYSICAL_KEYBOARD_COMPOSITION" to "PASS",
                "ONSCREEN_KEYBOARD_FALLBACK" to "PASS",
                "ALT_SYM_FN_TEXT_ENTRY" to "PASS",
                "LATEST_ANCHORED_CONVERSATION" to "PASS",
                "LATEST_MESSAGE_ALWAYS_VISIBLE" to "PASS",
                "OPEN_THREAD_AT_LATEST" to "PASS",
                "RESTORE_THREAD_AT_LATEST" to "PASS",
                "AUTO_ADVANCE_TO_LATEST_ON_INCOMING" to "PASS",
                "AUTO_ADVANCE_TO_LATEST_ON_SEND" to "PASS",
                "SCROLLING_REQUIRED_TO_SEE_LATEST_MESSAGE" to "NO",
                "CONTINUOUS_TOUCHPAD_SCROLLING_REQUIRED" to "NO",
                "HISTORY_ACCESS_EXPLICIT" to "PASS",
                "SEARCH_IN_CONVERSATION" to "PASS",
                "JUMP_TO_UNREAD" to "PASS",
                "RETURN_TO_LATEST_KEY" to "PASS",
                "CONVERSATION_LIST_LATEST_FIRST" to "PASS",
                "MESSAGE_ACTIONS_KEYBOARD_ACCESSIBLE" to "PASS",
                "CONTACTS_HANDOFF" to "PASS",
                "PHONE_HANDOFF" to "PASS",
                "HUB_HANDOFF" to "PASS",
                "COMMAND_SEARCH_HANDOFF" to "PASS",
                "NOTIFICATION_SHADE_HANDOFF" to "PASS",
                "LOCKSCREEN_PRIVACY_COMPATIBLE" to "PASS",
                "LOCKED_MODE_REDACTION" to "PASS",
                "PRIVATE_MODE_REDACTION" to "PASS",
                "SOURCE_RESTRICTED_MODE" to "PASS",
                "NO_FOCUS_TRAPS" to "PASS",
                "NO_MESSAGE_DEAD_ENDS" to "PASS",
                "CUSTOM_SMS_STACK" to "NO",
                "CUSTOM_RCS_STACK" to "NO",
                "CUSTOM_MMS_STACK" to "NO",
                "UNSUPPORTED_PROVIDER_ACTIONS" to "NO",
                "TITAN2_HARDWARE_TEMPLATE" to "PASS",
                "SQUARE_DISPLAY_PLUS_PHYSICAL_KEYBOARD" to "PASS",
                "NO_SLAB_PHONE_VISUAL_TARGET" to "PASS",
                "NO_STRETCHED_SCREEN_TARGET" to "PASS",
                "BUILD_CODE_CHANGED" to "NO",
                "DEVICE_CODE_CHANGED" to "NO",
                "FLASH_ENABLEMENT" to "NO",
            )

        assertEquals(expected, MessagesInteractionModel.releaseValidation)
    }

    @Test
    fun openRestoreIncomingAndSendStayAnchoredToLatest() {
        var state = MessagesInteractionModel.initialState()
        assertLatest(state)

        state = MessagesInteractionModel.reduce(state, MessagesAction.OpenThread)
        assertLatest(state)

        state = MessagesInteractionModel.reduce(state, MessagesAction.RestoreThread)
        assertLatest(state)
        assertEquals("RETURN_TO_LATEST_KEY", state.lastEvent)

        state = MessagesInteractionModel.reduce(state, MessagesAction.IncomingMessage)
        assertLatest(state)
        assertTrue(state.autoAdvancedOnIncoming)

        state = MessagesInteractionModel.reduce(state, MessagesAction.SendMessage)
        assertLatest(state)
        assertTrue(state.autoAdvancedOnSend)
        assertTrue(state.sentMessageVisibleImmediately)
    }

    @Test
    fun explicitHistoryAndSearchNeverRequireTouchpadScroll() {
        var state = MessagesInteractionModel.initialState()
        state = MessagesInteractionModel.reduce(state, MessagesAction.SearchInConversation)
        assertEquals(MessagesMode.Search, state.mode)
        assertEquals(MessagesFocus.SearchField, state.focus)
        assertTrue(state.historyAccessExplicit)
        assertTrue(state.noTouchpadScrollDependency)

        state = MessagesInteractionModel.reduce(state, MessagesAction.JumpToDate)
        assertEquals(MessagesMode.History, state.mode)
        assertEquals(1, state.historySlice)
        assertEquals(MessagesFocus.HistorySlice, state.focus)

        state = MessagesInteractionModel.reduce(state, MessagesAction.PageUpHistory)
        assertEquals(2, state.historySlice)
        state = MessagesInteractionModel.reduce(state, MessagesAction.PageDownTowardLatest)
        assertEquals(1, state.historySlice)

        state = MessagesInteractionModel.reduce(state, MessagesAction.IncomingMessage)
        assertTrue(state.newMessageIndicator)
        assertEquals("HISTORY_MODE_SHOWS_NEW_MESSAGE_INDICATOR", state.lastEvent)

        state = MessagesInteractionModel.reduce(state, MessagesAction.ReturnToLatest)
        assertLatest(state)
        assertFalse(state.newMessageIndicator)
    }

    @Test
    fun jumpTargetsAndLatestKeyAreAvailable() {
        val date =
            MessagesInteractionModel.reduce(
                MessagesInteractionModel.initialState(),
                MessagesAction.JumpToDate,
            )
        val unread = MessagesInteractionModel.reduce(date, MessagesAction.JumpToUnread)
        val nextUnread = MessagesInteractionModel.reduce(unread, MessagesAction.NextUnread)
        val attachment = MessagesInteractionModel.reduce(nextUnread, MessagesAction.JumpToAttachment)
        val latest = MessagesInteractionModel.reduce(attachment, MessagesAction.BackOrEsc)

        assertEquals("JUMP_TO_DATE", date.lastEvent)
        assertEquals("JUMP_TO_UNREAD", unread.lastEvent)
        assertEquals("NEXT_UNREAD", nextUnread.lastEvent)
        assertEquals(MessagesMode.AttachmentActions, attachment.mode)
        assertLatest(latest)
    }

    @Test
    fun conversationListIsLatestFirstAndKeyboardAddressable() {
        val threads = MessagesInteractionModel.latestFirstThreads(sampleThreads)
        assertEquals("Maya Patel", threads.first().person)

        var state = MessagesInteractionModel.initialState()
        state =
            MessagesInteractionModel.reduce(
                state,
                MessagesAction.ConversationListDown,
                threadCount = threads.size,
            )
        assertEquals(MessagesMode.ConversationList, state.mode)
        assertEquals(MessagesFocus.ConversationRow, state.focus)
        assertEquals(1, state.focusedConversationRow)

        state =
            MessagesInteractionModel.reduce(
                state,
                MessagesAction.ConversationListUp,
                threadCount = threads.size,
            )
        assertEquals(0, state.focusedConversationRow)

        state =
            MessagesInteractionModel.reduce(
                state.copy(focusedConversationRow = 2),
                MessagesAction.ConversationListHome,
                threadCount = threads.size,
            )
        assertEquals(0, state.focusedConversationRow)

        state =
            MessagesInteractionModel.reduce(
                state,
                MessagesAction.ConversationListEnd,
                threadCount = threads.size,
            )
        assertEquals(threads.lastIndex, state.focusedConversationRow)

        state = MessagesInteractionModel.reduce(state, MessagesAction.TypeToFilter)
        assertEquals(MessagesMode.Search, state.mode)

        state =
            MessagesInteractionModel.reduce(
                state.copy(focusedConversationRow = 2),
                MessagesAction.OpenFocusedConversation,
                threadCount = threads.size,
            )
        assertLatest(state)
        assertEquals(2, state.selectedThreadIndex)
    }

    @Test
    fun composerSupportsPhysicalKeyboardFallbackAndDraftSafety() {
        var state = MessagesInteractionModel.initialState()
        state = MessagesInteractionModel.reduce(state, MessagesAction.ComposerTyping)
        assertEquals(MessagesMode.Composer, state.mode)
        assertTrue(state.physicalKeyboardComposition)

        val typed = state.draft
        state = MessagesInteractionModel.reduce(state, MessagesAction.ComposerShiftEnterNewline)
        assertTrue(state.draft.startsWith(typed))
        assertTrue(state.draft.contains("\n"))

        state = MessagesInteractionModel.reduce(state, MessagesAction.ComposerBackspace)
        state = MessagesInteractionModel.reduce(state, MessagesAction.ComposerDelete)
        assertEquals(MessagesMode.Composer, state.mode)

        state = MessagesInteractionModel.reduce(state, MessagesAction.OnscreenKeyboardFallback)
        assertTrue(state.onscreenKeyboardFallback)
        assertTrue(state.physicalAndOnscreenDraftShareState)
        assertEquals(MessagesFocus.Composer, state.focus)

        state = MessagesInteractionModel.reduce(state, MessagesAction.AltSymFnTextEntry)
        assertTrue(state.altSymFnTextEntry)

        state = MessagesInteractionModel.reduce(state, MessagesAction.ComposerMenuActions)
        assertEquals(MessagesMode.MessageActions, state.mode)

        state = MessagesInteractionModel.reduce(state, MessagesAction.ComposerEnterSend)
        assertLatest(state)
        assertTrue(state.autoAdvancedOnSend)

        state = MessagesInteractionModel.reduce(state, MessagesAction.ComposerAltEnterProviderSend)
        assertLatest(state)
        assertTrue(state.autoAdvancedOnSend)
    }

    @Test
    fun messageAndAttachmentActionsAreKeyboardReachableAndProviderSafe() {
        var state = MessagesInteractionModel.initialState()
        state = MessagesInteractionModel.reduce(state, MessagesAction.MessageFocusUp)
        assertEquals(MessagesFocus.LatestMessage, state.focus)
        state = MessagesInteractionModel.reduce(state, MessagesAction.MessageFocusDown)
        assertEquals(MessagesFocus.LatestMessage, state.focus)

        listOf(
            MessagesAction.MessageEnterDetails,
            MessagesAction.MessageSpacePreview,
            MessagesAction.MessageMenuActions,
            MessagesAction.CopyText,
            MessagesAction.ReplyIfProviderSafe,
            MessagesAction.ForwardIfProviderSafe,
            MessagesAction.DeleteIfProviderSafe,
            MessagesAction.DetailsIfProviderSafe,
            MessagesAction.AttachmentActions,
            MessagesAction.CameraHandoff,
            MessagesAction.MediaPicker,
        ).forEach { action ->
            state = MessagesInteractionModel.reduce(state, action)
            assertTrue(state.providerSafeAction)
            assertFalse(state.unsupportedProviderActions)
        }

        assertTrue(state.attachmentActionsProviderSafe)
        assertTrue(state.cameraHandoffProviderSafe)
        assertTrue(state.mediaPickerPermissionAware)
    }

    @Test
    fun handoffsRetainProviderOwnershipAndPrivacyPolicy() {
        var state = MessagesInteractionModel.initialState()
        val handoffs =
            listOf(
                MessagesAction.ContactsHandoff,
                MessagesAction.PhoneHandoff,
                MessagesAction.HubHandoff,
                MessagesAction.CommandSearchHandoff,
                MessagesAction.NotificationShadeHandoff,
            )

        handoffs.forEach { action ->
            state = MessagesInteractionModel.reduce(state, action)
            assertTrue(state.providerOwnershipRetained)
            assertTrue(state.providerSafeAction)
            assertFalse(state.unsupportedProviderActions)
        }

        state = MessagesInteractionModel.reduce(state, MessagesAction.LockscreenHandoff)
        assertEquals(MessagesMode.Lockscreen, state.mode)
        assertTrue(state.messageBodyHiddenWhenLocked)

        state = MessagesInteractionModel.reduce(state, MessagesAction.LockedModeRedaction)
        assertTrue(state.lockedModeRedaction)
        assertTrue(state.messageBodyHiddenWhenLocked)

        state = MessagesInteractionModel.reduce(state, MessagesAction.PrivateModeRedaction)
        assertTrue(state.privateModeRedaction)

        state = MessagesInteractionModel.reduce(state, MessagesAction.SourceRestrictedMode)
        assertTrue(state.sourceRestrictedMode)
        assertTrue(state.contactNamePolicyAware)
        assertTrue(state.hubPrivacyCompatible)
        assertTrue(state.notificationShadePrivacyCompatible)
    }

    @Test
    fun customCarrierStacksAndUnsupportedProviderActionsStayDisabled() {
        val state = MessagesInteractionModel.initialState()
        assertFalse(state.customSmsStack)
        assertFalse(state.customRcsStack)
        assertFalse(state.customMmsStack)
        assertFalse(state.unsupportedProviderActions)
        assertTrue(state.providerOwnershipRetained)
    }

    @Test
    fun allModesHaveReturnToLatestAndNoDeadEnds() {
        MessagesAction.values().forEach { action ->
            val entered = MessagesInteractionModel.reduce(MessagesInteractionModel.initialState(), action)
            val latest = MessagesInteractionModel.reduce(entered, MessagesAction.ReturnToLatest)
            assertTrue(entered.noFocusTraps)
            assertTrue(entered.noMessageDeadEnds)
            assertLatest(latest)
        }
    }

    private fun assertLatest(state: MessagesUiState) {
        assertEquals(MessagesMode.Latest, state.mode)
        assertEquals(MessagesFocus.Composer, state.focus)
        assertTrue(state.latestMessageAlwaysVisible)
        assertTrue(state.composerVisibleWithLatest)
        assertTrue(state.noTouchpadScrollDependency)
        assertTrue(state.noFocusTraps)
        assertTrue(state.noMessageDeadEnds)
    }
}
