package org.sableos.assistant.core

/**
 * Hold-to-talk, the parts that are plain logic: the speech model to fetch, the recognizer's JSON,
 * and where the heard words go. Recognition runs on the phone (Vosk); no sound leaves it.
 */
object Speech {
    const val SAMPLE_RATE = 16_000f

    /** Vosk's small English model (Apache-2.0), fetched once when the owner asks for it. */
    const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    const val MODEL_MB = 40

    /** The words in one of the recognizer's results, such as {"text": "good morning"}. */
    fun textOf(json: String): String = (Json.at(Json.parse(json), "text") as? String).orEmpty().trim()

    fun join(parts: List<String>): String = parts.map(String::trim).filter(String::isNotEmpty).joinToString(" ")

    /** Heard words join what is already typed; they wait there to be read and sent. */
    fun appendToDraft(
        draft: String,
        heard: String,
    ): String = if (draft.isBlank()) heard else "${draft.trimEnd()} $heard"

    /**
     * Where a file of the model archive goes inside the model folder: the archive's own top
     * folder is dropped. Null for the top folder itself and for any path that would leave the
     * model folder.
     */
    fun entryPath(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter(String::isNotEmpty)
        val inside = parts.drop(1)
        return if (inside.isEmpty() || inside.any { it == ".." || it == "." }) null else inside.joinToString("/")
    }
}
