package org.sableos.hub.policy

import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/**
 * Priority Hub, set from a row's menu: one sender (an SMS number, or a name in one connected
 * app) or every row of a connected app (that app's Hub priority, as in Connected apps). Priority
 * rows show under the Priority filter and sort first; it orders Hub only and is never a Do Not
 * Disturb exception. The sender keys stay on the phone in Hub's own preferences.
 */
object HubPriority {
    private const val SMS = "sms:"
    private const val APP = "app:"

    /** The sender's key, from the unredacted row; null when the row names no sender. */
    fun personKey(conversation: HubConversation): String? =
        if (conversation.source == HubConversationSource.Sms) {
            val number = conversation.address.filter { it.isDigit() || it == '+' }
            val address = number.ifEmpty { conversation.address.trim().lowercase() }
            address.takeIf { it.isNotEmpty() }?.let { SMS + it }
        } else {
            val name = conversation.displayName.trim().lowercase()
            val pkg = conversation.sourcePackage
            val serial = conversation.sourceUserSerial
            if (pkg == null || serial == null || name.isEmpty()) null else "$APP$pkg:$serial:$name"
        }

    /** The row with its sender key and Priority Hub state; [appPriority] is the app's own Hub priority. */
    fun apply(
        conversation: HubConversation,
        people: Set<String>,
        appPriority: Boolean = false,
    ): HubConversation {
        val key = personKey(conversation)
        val person = key != null && key in people
        return conversation.copy(
            priorityKey = key,
            personPriority = person,
            appPriority = appPriority,
            hubPriority = person || appPriority,
        )
    }

    /** Whether the row can add its whole app: connected apps only, Hub's own SMS has no app switch. */
    fun canAddApp(conversation: HubConversation): Boolean =
        conversation.source != HubConversationSource.Sms &&
            conversation.sourcePackage != null &&
            conversation.sourceUserSerial != null

    /** The two row menu entries for [conversation], add or remove as it stands. */
    fun menuActions(conversation: HubConversation): List<HubRowAction> =
        buildList {
            if (conversation.priorityKey != null) {
                val person = conversation.personPriority
                add(if (person) HubRowAction.RemovePersonPriority else HubRowAction.AddPersonPriority)
            }
            if (canAddApp(conversation)) {
                add(if (conversation.appPriority) HubRowAction.RemoveAppPriority else HubRowAction.AddAppPriority)
            }
        }
}
