package org.sableos.textreader.speak

/** What "Speak what's focused" knows about the item that took focus. Pure data, no Android types. */
data class FocusFacts(
    val text: String? = null,
    val description: String? = null,
    val hint: String? = null,
    val role: FocusRole = FocusRole.Other,
    val password: Boolean = false,
    val checked: Boolean? = null,
    val state: String? = null,
    val locked: Boolean = false,
)

enum class FocusRole { Button, Switch, CheckBox, Edit, Other }

/** The words the service adds after a label, from the app's strings. */
data class FocusWords(
    val button: String,
    val switch: String,
    val checkBox: String,
    val editBox: String,
    val on: String,
    val off: String,
    val checked: String,
    val notChecked: String,
    val password: String,
) {
    companion object {
        val EN =
            FocusWords(
                button = "button",
                switch = "switch",
                checkBox = "check box",
                editBox = "edit box",
                on = "on",
                off = "off",
                checked = "checked",
                notChecked = "not checked",
                password = "Password field",
            )
    }
}

/**
 * Turns a focus change into one short utterance.
 *
 * Privacy: nothing is spoken while the phone is locked, so a PIN pad or a lock screen notification
 * is never read aloud, and a password field is named but its characters never are.
 */
object FocusSpeech {
    const val MAX_CHARS = 300
    const val REPEAT_MILLIS = 800L

    fun roleOf(
        className: String?,
        editable: Boolean,
        checkable: Boolean,
    ): FocusRole {
        val name = className.orEmpty()
        return when {
            name.endsWith("Switch") || name.endsWith("SwitchCompat") || name.endsWith("SwitchMaterial") -> FocusRole.Switch
            name.endsWith("CheckBox") -> FocusRole.CheckBox
            editable || name.endsWith("EditText") -> FocusRole.Edit
            name.endsWith("Button") -> FocusRole.Button
            checkable -> FocusRole.CheckBox
            else -> FocusRole.Other
        }
    }

    fun utterance(
        facts: FocusFacts,
        words: FocusWords = FocusWords.EN,
    ): String? {
        if (facts.locked) return null
        if (facts.password) {
            val name = clean(facts.description) ?: clean(facts.hint)
            return listOfNotNull(name, words.password).joinToString(", ")
        }
        val label =
            when (facts.role) {
                FocusRole.Edit -> clean(facts.description) ?: clean(facts.hint)
                else -> clean(facts.description) ?: clean(facts.text) ?: clean(facts.hint)
            }
        val typed = if (facts.role == FocusRole.Edit) clean(facts.text) else null
        val role =
            when (facts.role) {
                FocusRole.Button -> words.button
                FocusRole.Switch -> words.switch
                FocusRole.CheckBox -> words.checkBox
                FocusRole.Edit -> words.editBox
                FocusRole.Other -> null
            }
        val state =
            when (facts.role) {
                FocusRole.Switch -> facts.checked?.let { if (it) words.on else words.off }
                FocusRole.CheckBox -> facts.checked?.let { if (it) words.checked else words.notChecked }
                else -> null
            } ?: clean(facts.state)
        if (label == null && typed == null && facts.role == FocusRole.Other) return null
        val spoken = listOfNotNull(label, role, state, typed).joinToString(", ")
        return if (spoken.length <= MAX_CHARS) spoken else spoken.take(MAX_CHARS - 1).trimEnd() + "…"
    }

    /** The same utterance again within [REPEAT_MILLIS] is an echo (focus and selection events), not news. */
    fun shouldSpeak(
        last: String?,
        lastAtMillis: Long,
        next: String,
        nowMillis: Long,
    ): Boolean = next != last || nowMillis - lastAtMillis >= REPEAT_MILLIS

    private fun clean(value: String?): String? = value?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}
