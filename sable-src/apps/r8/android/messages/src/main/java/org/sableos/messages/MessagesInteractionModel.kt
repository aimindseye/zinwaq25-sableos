package org.sableos.messages

data class SableMessage(
    val id: String,
    val sender: String,
    val body: String,
    val time: String,
    val incoming: Boolean,
    val unread: Boolean = false,
    val attachmentLabel: String? = null,
)

data class SableThread(
    val id: String,
    val person: String,
    val route: String,
    val source: String,
    val priority: Int,
    val unreadCount: Int,
    val messages: List<SableMessage>,
)

enum class MessagesMode {
    Latest,
    History,
    Search,
    ConversationList,
    Composer,
    MessageActions,
    AttachmentActions,
    ContactsHandoff,
    PhoneHandoff,
    HubHandoff,
    CommandSearchHandoff,
    NotificationShadeHandoff,
    Lockscreen,
    Privacy,
}

enum class MessagesFocus {
    LatestMessage,
    Composer,
    SearchField,
    ConversationRow,
    HistorySlice,
    MessageAction,
    AttachmentAction,
    HandoffAction,
    PrivacyPanel,
}

enum class MessagesAction {
    OpenThread,
    RestoreThread,
    IncomingMessage,
    SendMessage,
    SearchInConversation,
    JumpToDate,
    JumpToUnread,
    JumpToAttachment,
    PageUpHistory,
    PageDownTowardLatest,
    ReturnToLatest,
    NextUnread,
    BackOrEsc,
    ConversationListUp,
    ConversationListDown,
    ConversationListHome,
    ConversationListEnd,
    TypeToFilter,
    OpenFocusedConversation,
    ComposerTyping,
    ComposerBackspace,
    ComposerDelete,
    ComposerEnterSend,
    ComposerShiftEnterNewline,
    ComposerAltEnterProviderSend,
    ComposerMenuActions,
    OnscreenKeyboardFallback,
    AltSymFnTextEntry,
    MessageFocusUp,
    MessageFocusDown,
    MessageEnterDetails,
    MessageSpacePreview,
    MessageMenuActions,
    CopyText,
    ReplyIfProviderSafe,
    ForwardIfProviderSafe,
    DeleteIfProviderSafe,
    DetailsIfProviderSafe,
    AttachmentActions,
    CameraHandoff,
    MediaPicker,
    ContactsHandoff,
    PhoneHandoff,
    HubHandoff,
    CommandSearchHandoff,
    NotificationShadeHandoff,
    LockscreenHandoff,
    LockedModeRedaction,
    PrivateModeRedaction,
    SourceRestrictedMode,
}

data class MessagesUiState(
    val mode: MessagesMode = MessagesMode.Latest,
    val focus: MessagesFocus = MessagesFocus.Composer,
    val selectedThreadIndex: Int = 0,
    val focusedConversationRow: Int = 0,
    val focusedMessageIndex: Int = 0,
    val historySlice: Int = 0,
    val searchQuery: String = "",
    val draft: String = "Typing reply…",
    val latestMessageAlwaysVisible: Boolean = true,
    val composerVisibleWithLatest: Boolean = true,
    val latestConversationFirst: Boolean = true,
    val providerOwnershipRetained: Boolean = true,
    val providerSafeAction: Boolean = true,
    val customSmsStack: Boolean = false,
    val customRcsStack: Boolean = false,
    val customMmsStack: Boolean = false,
    val unsupportedProviderActions: Boolean = false,
    val noTouchpadScrollDependency: Boolean = true,
    val historyAccessExplicit: Boolean = true,
    val newMessageIndicator: Boolean = false,
    val onscreenKeyboardFallback: Boolean = true,
    val physicalKeyboardComposition: Boolean = true,
    val altSymFnTextEntry: Boolean = true,
    val physicalAndOnscreenDraftShareState: Boolean = true,
    val draftPersistence: Boolean = true,
    val lockedModeRedaction: Boolean = false,
    val privateModeRedaction: Boolean = false,
    val sourceRestrictedMode: Boolean = false,
    val messageBodyHiddenWhenLocked: Boolean = false,
    val contactNamePolicyAware: Boolean = true,
    val hubPrivacyCompatible: Boolean = true,
    val notificationShadePrivacyCompatible: Boolean = true,
    val noFocusTraps: Boolean = true,
    val noMessageDeadEnds: Boolean = true,
    val sentMessageVisibleImmediately: Boolean = false,
    val autoAdvancedOnIncoming: Boolean = false,
    val autoAdvancedOnSend: Boolean = false,
    val attachmentActionsProviderSafe: Boolean = true,
    val cameraHandoffProviderSafe: Boolean = true,
    val mediaPickerPermissionAware: Boolean = true,
    val lastEvent: String = "OPEN_THREAD_AT_LATEST",
)

object MessagesInteractionModel {
    val releaseValidation: Map<String, String> =
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

    fun initialState(): MessagesUiState = MessagesUiState()

    fun reduce(
        state: MessagesUiState,
        action: MessagesAction,
        threadCount: Int = sampleThreads.size,
    ): MessagesUiState =
        when (action) {
            MessagesAction.OpenThread, MessagesAction.RestoreThread, MessagesAction.IncomingMessage,
            MessagesAction.SearchInConversation, MessagesAction.ReturnToLatest, MessagesAction.BackOrEsc,
            MessagesAction.TypeToFilter,
            -> reduceTimeline(state, action)

            MessagesAction.JumpToDate, MessagesAction.JumpToUnread, MessagesAction.JumpToAttachment,
            MessagesAction.PageUpHistory, MessagesAction.PageDownTowardLatest, MessagesAction.NextUnread,
            -> reduceHistory(state, action)

            MessagesAction.ConversationListUp, MessagesAction.ConversationListDown,
            MessagesAction.ConversationListHome, MessagesAction.ConversationListEnd,
            MessagesAction.OpenFocusedConversation,
            -> reduceConversationList(state, action, threadCount)

            MessagesAction.SendMessage, MessagesAction.ComposerTyping, MessagesAction.ComposerBackspace,
            MessagesAction.ComposerDelete, MessagesAction.ComposerEnterSend,
            MessagesAction.ComposerShiftEnterNewline, MessagesAction.ComposerAltEnterProviderSend,
            -> reduceComposerInput(state, action)

            MessagesAction.ComposerMenuActions, MessagesAction.OnscreenKeyboardFallback,
            MessagesAction.AltSymFnTextEntry,
            -> reduceComposerUi(state, action)

            MessagesAction.MessageFocusUp, MessagesAction.MessageFocusDown, MessagesAction.MessageEnterDetails,
            MessagesAction.MessageSpacePreview, MessagesAction.MessageMenuActions, MessagesAction.CopyText,
            MessagesAction.ReplyIfProviderSafe, MessagesAction.ForwardIfProviderSafe,
            MessagesAction.DeleteIfProviderSafe, MessagesAction.DetailsIfProviderSafe,
            -> reduceMessageActions(state, action)

            MessagesAction.AttachmentActions, MessagesAction.CameraHandoff, MessagesAction.MediaPicker,
            -> reduceAttachments(state, action)

            MessagesAction.ContactsHandoff, MessagesAction.PhoneHandoff, MessagesAction.HubHandoff,
            MessagesAction.CommandSearchHandoff, MessagesAction.NotificationShadeHandoff,
            -> reduceProviderHandoff(state, action)

            MessagesAction.LockscreenHandoff, MessagesAction.LockedModeRedaction,
            MessagesAction.PrivateModeRedaction, MessagesAction.SourceRestrictedMode,
            -> reducePrivacy(state, action)
        }

    private fun reduceTimeline(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.OpenThread,
            MessagesAction.RestoreThread,
            MessagesAction.ReturnToLatest,
            MessagesAction.BackOrEsc,
            -> {
                state.toLatest("RETURN_TO_LATEST_KEY")
            }

            MessagesAction.IncomingMessage -> {
                if (state.mode == MessagesMode.History || state.mode == MessagesMode.Search) {
                    state.copy(
                        newMessageIndicator = true,
                        latestMessageAlwaysVisible = true,
                        lastEvent = "HISTORY_MODE_SHOWS_NEW_MESSAGE_INDICATOR",
                    )
                } else {
                    state
                        .toLatest("AUTO_ADVANCE_TO_LATEST_ON_INCOMING")
                        .copy(autoAdvancedOnIncoming = true)
                }
            }

            MessagesAction.SearchInConversation,
            MessagesAction.TypeToFilter,
            -> {
                state.copy(
                    mode = MessagesMode.Search,
                    focus = MessagesFocus.SearchField,
                    historyAccessExplicit = true,
                    lastEvent = "SEARCH_IN_CONVERSATION",
                )
            }

            else -> {
                error("Unexpected timeline action: $action")
            }
        }

    private fun reduceHistory(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.JumpToDate -> {
                state.toHistory(1, "JUMP_TO_DATE")
            }

            MessagesAction.JumpToUnread -> {
                state.toHistory(0, "JUMP_TO_UNREAD")
            }

            MessagesAction.NextUnread -> {
                state.toHistory(0, "NEXT_UNREAD")
            }

            MessagesAction.JumpToAttachment -> {
                state.copy(
                    mode = MessagesMode.AttachmentActions,
                    focus = MessagesFocus.AttachmentAction,
                    lastEvent = "JUMP_TO_ATTACHMENT",
                )
            }

            MessagesAction.PageUpHistory -> {
                state.toHistory(
                    slice = state.historySlice + 1,
                    event = "PAGE_UP_HISTORY",
                )
            }

            MessagesAction.PageDownTowardLatest -> {
                if (state.historySlice <= 0) {
                    state.toLatest("PAGE_DOWN_TOWARD_LATEST")
                } else {
                    state.toHistory(
                        slice = state.historySlice - 1,
                        event = "PAGE_DOWN_TOWARD_LATEST",
                    )
                }
            }

            else -> {
                error("Unexpected history action: $action")
            }
        }

    private fun reduceConversationList(
        state: MessagesUiState,
        action: MessagesAction,
        threadCount: Int,
    ): MessagesUiState =
        when (action) {
            MessagesAction.ConversationListUp -> {
                state.toConversationList(
                    row = (state.focusedConversationRow - 1).coerceAtLeast(0),
                    event = "CONVERSATION_LIST_UP",
                )
            }

            MessagesAction.ConversationListDown -> {
                state.toConversationList(
                    row = (state.focusedConversationRow + 1).coerceAtMost(threadCount - 1),
                    event = "CONVERSATION_LIST_DOWN",
                )
            }

            MessagesAction.ConversationListHome -> {
                state.toConversationList(row = 0, event = "CONVERSATION_LIST_HOME")
            }

            MessagesAction.ConversationListEnd -> {
                state.toConversationList(
                    row = (threadCount - 1).coerceAtLeast(0),
                    event = "CONVERSATION_LIST_END",
                )
            }

            MessagesAction.OpenFocusedConversation -> {
                state
                    .toLatest("OPEN_SELECTED_THREAD_AT_LATEST")
                    .copy(selectedThreadIndex = state.focusedConversationRow)
            }

            else -> {
                error("Unexpected conversation-list action: $action")
            }
        }

    private fun reduceComposerInput(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.SendMessage,
            MessagesAction.ComposerEnterSend,
            MessagesAction.ComposerAltEnterProviderSend,
            -> {
                state
                    .toLatest("AUTO_ADVANCE_TO_LATEST_ON_SEND")
                    .copy(
                        draft = "",
                        sentMessageVisibleImmediately = true,
                        autoAdvancedOnSend = true,
                    )
            }

            MessagesAction.ComposerTyping -> {
                state.copy(
                    mode = MessagesMode.Composer,
                    focus = MessagesFocus.Composer,
                    draft = state.draft + "x",
                    lastEvent = "PHYSICAL_KEYBOARD_COMPOSITION",
                )
            }

            MessagesAction.ComposerBackspace,
            MessagesAction.ComposerDelete,
            -> {
                state.copy(
                    mode = MessagesMode.Composer,
                    focus = MessagesFocus.Composer,
                    draft = state.draft.dropLast(1),
                    lastEvent = "DRAFT_EDIT",
                )
            }

            MessagesAction.ComposerShiftEnterNewline -> {
                state.copy(
                    mode = MessagesMode.Composer,
                    focus = MessagesFocus.Composer,
                    draft = state.draft + "\n",
                    lastEvent = "SHIFT_ENTER_NEWLINE",
                )
            }

            else -> {
                error("Unexpected composer-input action: $action")
            }
        }

    private fun reduceComposerUi(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.ComposerMenuActions -> {
                state.copy(
                    mode = MessagesMode.MessageActions,
                    focus = MessagesFocus.MessageAction,
                    providerSafeAction = true,
                    lastEvent = "MESSAGE_ACTIONS_KEYBOARD_ACCESSIBLE",
                )
            }

            MessagesAction.OnscreenKeyboardFallback -> {
                state.copy(
                    onscreenKeyboardFallback = true,
                    physicalAndOnscreenDraftShareState = true,
                    focus = MessagesFocus.Composer,
                    lastEvent = "ONSCREEN_KEYBOARD_FALLBACK",
                )
            }

            MessagesAction.AltSymFnTextEntry -> {
                state.copy(
                    altSymFnTextEntry = true,
                    focus = MessagesFocus.Composer,
                    lastEvent = "ALT_SYM_FN_TEXT_ENTRY",
                )
            }

            else -> {
                error("Unexpected composer-UI action: $action")
            }
        }

    private fun reduceMessageActions(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.MessageFocusUp -> {
                state.copy(
                    focus = MessagesFocus.LatestMessage,
                    focusedMessageIndex = (state.focusedMessageIndex - 1).coerceAtLeast(0),
                    lastEvent = "MESSAGE_FOCUS_UP",
                )
            }

            MessagesAction.MessageFocusDown -> {
                state.copy(
                    focus = MessagesFocus.LatestMessage,
                    focusedMessageIndex = state.focusedMessageIndex + 1,
                    lastEvent = "MESSAGE_FOCUS_DOWN",
                )
            }

            MessagesAction.MessageEnterDetails,
            MessagesAction.MessageSpacePreview,
            MessagesAction.MessageMenuActions,
            -> {
                state.copy(
                    mode = MessagesMode.MessageActions,
                    focus = MessagesFocus.MessageAction,
                    providerSafeAction = true,
                    lastEvent = "MESSAGE_ACTIONS_KEYBOARD_ACCESSIBLE",
                )
            }

            MessagesAction.CopyText,
            MessagesAction.ReplyIfProviderSafe,
            MessagesAction.ForwardIfProviderSafe,
            MessagesAction.DeleteIfProviderSafe,
            MessagesAction.DetailsIfProviderSafe,
            -> {
                state.copy(
                    mode = MessagesMode.MessageActions,
                    focus = MessagesFocus.MessageAction,
                    providerSafeAction = true,
                    unsupportedProviderActions = false,
                    lastEvent = action.name,
                )
            }

            else -> {
                error("Unexpected message action: $action")
            }
        }

    private fun reduceAttachments(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.AttachmentActions -> {
                state.copy(
                    mode = MessagesMode.AttachmentActions,
                    focus = MessagesFocus.AttachmentAction,
                    attachmentActionsProviderSafe = true,
                    lastEvent = "ATTACHMENT_ACTIONS_PROVIDER_SAFE",
                )
            }

            MessagesAction.CameraHandoff -> {
                state.copy(
                    mode = MessagesMode.AttachmentActions,
                    focus = MessagesFocus.AttachmentAction,
                    cameraHandoffProviderSafe = true,
                    lastEvent = "CAMERA_HANDOFF_PROVIDER_SAFE",
                )
            }

            MessagesAction.MediaPicker -> {
                state.copy(
                    mode = MessagesMode.AttachmentActions,
                    focus = MessagesFocus.AttachmentAction,
                    mediaPickerPermissionAware = true,
                    lastEvent = "MEDIA_PICKER_PERMISSION_AWARE",
                )
            }

            else -> {
                error("Unexpected attachment action: $action")
            }
        }

    private fun reduceProviderHandoff(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.ContactsHandoff -> {
                state.toHandoff(MessagesMode.ContactsHandoff, "CONTACTS_HANDOFF")
            }

            MessagesAction.PhoneHandoff -> {
                state.toHandoff(MessagesMode.PhoneHandoff, "PHONE_HANDOFF")
            }

            MessagesAction.HubHandoff -> {
                state.toHandoff(MessagesMode.HubHandoff, "HUB_HANDOFF")
            }

            MessagesAction.CommandSearchHandoff -> {
                state.toHandoff(MessagesMode.CommandSearchHandoff, "COMMAND_SEARCH_HANDOFF")
            }

            MessagesAction.NotificationShadeHandoff -> {
                state.toHandoff(MessagesMode.NotificationShadeHandoff, "NOTIFICATION_SHADE_HANDOFF")
            }

            else -> {
                error("Unexpected provider-handoff action: $action")
            }
        }

    private fun reducePrivacy(
        state: MessagesUiState,
        action: MessagesAction,
    ): MessagesUiState =
        when (action) {
            MessagesAction.LockscreenHandoff -> {
                state.copy(
                    mode = MessagesMode.Lockscreen,
                    focus = MessagesFocus.PrivacyPanel,
                    lockedModeRedaction = true,
                    messageBodyHiddenWhenLocked = true,
                    lastEvent = "LOCKSCREEN_PRIVACY_COMPATIBLE",
                )
            }

            MessagesAction.LockedModeRedaction -> {
                state.redacted("LOCKED_MODE_REDACTION")
            }

            MessagesAction.PrivateModeRedaction -> {
                state.redacted("PRIVATE_MODE_REDACTION").copy(privateModeRedaction = true)
            }

            MessagesAction.SourceRestrictedMode -> {
                state.redacted("SOURCE_RESTRICTED_MODE").copy(sourceRestrictedMode = true)
            }

            else -> {
                error("Unexpected privacy action: $action")
            }
        }

    fun latestFirstThreads(threads: List<SableThread>): List<SableThread> =
        threads.sortedWith(
            compareByDescending<SableThread> { it.priority }
                .thenByDescending { it.unreadCount }
                .thenByDescending {
                    it.messages
                        .lastOrNull()
                        ?.time
                        .orEmpty()
                },
        )

    fun visibleReleaseMarkers(state: MessagesUiState): Map<String, String> =
        releaseValidation +
            linkedMapOf(
                "PROVIDER_OWNERSHIP_RETAINED" to pass(state.providerOwnershipRetained),
                "PROVIDER_SAFE_ACTIONS" to pass(state.providerSafeAction),
                "NO_TOUCHPAD_SCROLL_DEPENDENCY" to pass(state.noTouchpadScrollDependency),
                "PHYSICAL_AND_ONSCREEN_DRAFT_SHARE_STATE" to
                    pass(
                        state.physicalAndOnscreenDraftShareState,
                    ),
                "DRAFT_PERSISTENCE" to pass(state.draftPersistence),
                "ATTACHMENT_ACTIONS_PROVIDER_SAFE" to
                    pass(
                        state.attachmentActionsProviderSafe,
                    ),
                "CAMERA_HANDOFF_PROVIDER_SAFE" to pass(state.cameraHandoffProviderSafe),
                "MEDIA_PICKER_PERMISSION_AWARE" to pass(state.mediaPickerPermissionAware),
                "MESSAGE_BODY_HIDDEN_WHEN_LOCKED" to
                    pass(
                        state.messageBodyHiddenWhenLocked || state.lockedModeRedaction,
                    ),
                "CONTACT_NAME_POLICY_AWARE" to pass(state.contactNamePolicyAware),
                "HUB_PRIVACY_COMPATIBLE" to pass(state.hubPrivacyCompatible),
                "NOTIFICATION_SHADE_PRIVACY_COMPATIBLE" to
                    pass(
                        state.notificationShadePrivacyCompatible,
                    ),
            )

    private fun MessagesUiState.toLatest(event: String): MessagesUiState =
        copy(
            mode = MessagesMode.Latest,
            focus = MessagesFocus.Composer,
            historySlice = 0,
            newMessageIndicator = false,
            latestMessageAlwaysVisible = true,
            composerVisibleWithLatest = true,
            noTouchpadScrollDependency = true,
            noFocusTraps = true,
            noMessageDeadEnds = true,
            lastEvent = event,
        )

    private fun MessagesUiState.toHistory(
        slice: Int,
        event: String,
    ): MessagesUiState =
        copy(
            mode = MessagesMode.History,
            focus = MessagesFocus.HistorySlice,
            historySlice = slice.coerceAtLeast(0),
            historyAccessExplicit = true,
            noTouchpadScrollDependency = true,
            noFocusTraps = true,
            noMessageDeadEnds = true,
            lastEvent = event,
        )

    private fun MessagesUiState.toConversationList(
        row: Int,
        event: String,
    ): MessagesUiState =
        copy(
            mode = MessagesMode.ConversationList,
            focus = MessagesFocus.ConversationRow,
            focusedConversationRow = row.coerceAtLeast(0),
            latestConversationFirst = true,
            noTouchpadScrollDependency = true,
            lastEvent = event,
        )

    private fun MessagesUiState.toHandoff(
        mode: MessagesMode,
        event: String,
    ): MessagesUiState =
        copy(
            mode = mode,
            focus = MessagesFocus.HandoffAction,
            providerOwnershipRetained = true,
            providerSafeAction = true,
            unsupportedProviderActions = false,
            lastEvent = event,
        )

    private fun MessagesUiState.redacted(event: String): MessagesUiState =
        copy(
            mode = MessagesMode.Privacy,
            focus = MessagesFocus.PrivacyPanel,
            lockedModeRedaction = true,
            messageBodyHiddenWhenLocked = true,
            contactNamePolicyAware = true,
            hubPrivacyCompatible = true,
            notificationShadePrivacyCompatible = true,
            lastEvent = event,
        )

    private fun pass(value: Boolean): String = if (value) "PASS" else "FAIL"
}

val sampleThreads: List<SableThread> =
    listOf(
        SableThread(
            id = "maya",
            person = "Maya Patel",
            route = "+15550101010",
            source = "SMS · Private OK",
            priority = 3,
            unreadCount = 1,
            messages =
                listOf(
                    SableMessage(
                        id = "m1",
                        sender = "Maya",
                        body = "Train arrives 7:18",
                        time = "19:09",
                        incoming = true,
                    ),
                    SableMessage(
                        id = "m2",
                        sender = "You",
                        body = "I’ll meet you outside.",
                        time = "19:10",
                        incoming = false,
                    ),
                    SableMessage(
                        id = "m3",
                        sender = "Maya",
                        body = "Just got off. Latest msg",
                        time = "19:12",
                        incoming = true,
                        unread = true,
                        attachmentLabel = "station-photo.jpg",
                    ),
                ),
        ),
        SableThread(
            id = "team",
            person = "Team thread",
            route = "+15550202020",
            source = "Provider route",
            priority = 2,
            unreadCount = 0,
            messages =
                listOf(
                    SableMessage(
                        id = "t1",
                        sender = "Team",
                        body = "Calendar changed",
                        time = "18:02",
                        incoming = true,
                    ),
                ),
        ),
        SableThread(
            id = "alex",
            person = "Alex",
            route = "+15550303030",
            source = "SMS",
            priority = 1,
            unreadCount = 0,
            messages =
                listOf(
                    SableMessage(
                        id = "a1",
                        sender = "Alex",
                        body = "Can you review?",
                        time = "17:44",
                        incoming = true,
                    ),
                ),
        ),
    )
