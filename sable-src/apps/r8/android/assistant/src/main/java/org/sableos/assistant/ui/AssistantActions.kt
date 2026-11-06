package org.sableos.assistant.ui

import org.sableos.assistant.core.AssistantSettings

/** What the screens ask the activity to do. */
interface AssistantActions {
    fun send(text: String)

    fun test(settings: AssistantSettings)

    fun save(settings: AssistantSettings)

    /** Hold-to-talk: the microphone is open only from [talkDown] to [talkUp]. */
    fun talkDown()

    fun talkUp()

    fun downloadVoice()

    fun removeVoice()
}
