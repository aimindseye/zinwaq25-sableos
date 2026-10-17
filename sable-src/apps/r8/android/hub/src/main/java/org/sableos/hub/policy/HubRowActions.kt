package org.sableos.hub.policy

import org.sableos.hub.HubCall
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/** One entry of the row actions sheet (Menu, Alt+Enter or long press on an inbox row). */
enum class HubRowAction(
    val label: String,
) {
    Open("Open"),
    Reply("Reply"),
    CallBack("Call back"),
    Text("Text"),
    OpenInMessages("Open in Messages"),
    OpenSourceApp("Open source app"),
    SourceNotifications("Notification settings"),
    PrivacyOptions("Privacy options"),
}

/**
 * The provider-safe actions of a Hub row (SABLE_HUB_KEYBOARD_FIRST_UX "Row actions"): only what
 * the source exposes and the current privacy state allows (`INVENT_UNSUPPORTED_ACTIONS=NO`).
 * Hub cannot mark SMS read, mute a source or change the call log, so those never appear.
 */
object HubRowActions {
    fun forConversation(
        conversation: HubConversation,
        privacy: HubPrivacyState = HubPrivacyState(),
    ): List<HubRowAction> {
        val actions = mutableListOf(HubRowAction.Open)
        // Replies and calls need the device unlocked (PrivacyPosture.requiresUnlockForActions).
        val unlocked = !privacy.deviceLocked
        if (conversation.source == HubConversationSource.Sms) {
            val address = conversation.address.isNotBlank()
            if (unlocked && address) actions += HubRowAction.Reply
            if (unlocked && address && conversation.address.any(Char::isDigit)) actions += HubRowAction.CallBack
            if (unlocked) actions += HubRowAction.OpenInMessages
        } else {
            if (unlocked && conversation.canQuickReply && conversation.sourceNotificationKey != null) {
                actions += HubRowAction.Reply
            }
            if (conversation.sourcePackage != null && conversation.sourceUserSerial != null) {
                if (unlocked) actions += HubRowAction.OpenSourceApp
                actions += HubRowAction.SourceNotifications
            }
        }
        actions += HubRowAction.PrivacyOptions
        return actions
    }

    /** A redacted call (no number) offers no callback or text: there is nothing to dial. */
    fun forCall(
        call: HubCall,
        privacy: HubPrivacyState = HubPrivacyState(),
    ): List<HubRowAction> {
        val actions = mutableListOf<HubRowAction>()
        if (call.number.isNotBlank() && !privacy.deviceLocked) {
            actions += HubRowAction.CallBack
            actions += HubRowAction.Text
        }
        actions += HubRowAction.PrivacyOptions
        return actions
    }

    fun forItem(
        item: HubInboxItem,
        privacy: HubPrivacyState = HubPrivacyState(),
    ): List<HubRowAction> =
        when (item) {
            is HubInboxItem.Conversation -> forConversation(item.conversation, privacy)
            is HubInboxItem.Call -> forCall(item.call, privacy)
        }

    /** Title of the sheet: the row's name as the (redacted) snapshot shows it. */
    fun title(item: HubInboxItem): String =
        when (item) {
            is HubInboxItem.Conversation -> item.conversation.displayName
            is HubInboxItem.Call -> item.call.displayName
        }
}
