package org.sableos.assistant.core

import org.sableos.design.SableTexts

/**
 * Every line Cosgrove shows, in English, by string resource name. res/values/strings.xml repeats
 * them (AssistantTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 * "%1$s" is his name unless the line says otherwise.
 */
object AssistantText {
    val EN: Map<String, String> =
        mapOf(
            "greeting" to "Good day. %1\$s at your service. How may I help?",
            "message_hint" to "Ask %1\$s",
            "thinking" to "%1\$s is considering…",
            "keys_hint" to "Enter sends · Shift+Enter new line · hold Space to talk · Menu settings",
            "send" to "Send",
            "settings" to "Settings",
            "new_chat" to "New conversation",
            "done" to "Done",
            "setup_first" to "Choose where my answers come from in Settings.",
            "name" to "Name",
            "name_detail" to "Command opens me with /%1\$s",
            "provider" to "Where my answers come from",
            "provider_ollama" to "Ollama on your network",
            "provider_ollama_detail" to "Free. Nothing leaves your home network.",
            "provider_custom" to "Other server on your network",
            "provider_custom_detail" to "LM Studio, llama.cpp or any server with an OpenAI-style API.",
            "provider_hosted_detail" to
                "Free plan with your own key. Your messages go to %1\$s; " +
                "free plans have limits and may use messages for training.",
            "address" to "Server address",
            "address_hint" to "For example 192.168.1.20",
            "ollama_address_detail" to
                "Your computer's address. Ollama must listen on the network: " +
                "set OLLAMA_HOST=0.0.0.0 and restart it.",
            "api_key" to "API key",
            "api_key_hint" to "Paste the key from the provider's website",
            "model" to "Model",
            "model_none" to "No model chosen. Test the connection to list them.",
            "test_connection" to "Test connection",
            "testing" to "Connecting…",
            "models_found" to "Connected. %1\$s models found; choose one below.",
            "no_models" to "Connected, but the server lists no models.",
            "error_provider" to "Choose where my answers come from first.",
            "error_address" to "Enter your server's address, for example 192.168.1.20.",
            "error_plain_http" to "Plain http only works on your own network. Use an https address.",
            "error_key" to "Enter your API key first.",
            "error_model" to "Choose a model in Settings first.",
            "error_unreachable" to "I couldn't reach the server. Check the address and that it is running.",
            "error_timeout" to "The server took too long to answer.",
            "error_server" to "The server answered: %1\$s",
            "privacy_note" to "Conversations stay in memory only and are gone when you start a new one.",
            "talk" to "Hold to talk",
            "listening" to "Listening… let go when you're done.",
            "voice" to "Voice",
            "voice_detail" to
                "Hold Space in an empty message box, or hold Talk, and speak (English for now). " +
                "Speech becomes text on this phone and waits for you to send it. Nothing listens unless you hold.",
            "voice_download" to "Download speech model (%1\$s MB)",
            "voice_downloading" to "Downloading the speech model…",
            "voice_ready" to "Speech model ready.",
            "voice_remove" to "Remove speech model",
            "voice_missing" to "To talk, download the speech model in Settings.",
            "voice_failed" to "The speech model couldn't be downloaded.",
            "voice_permission" to "Allow the microphone so I can hear you while you hold.",
            "voice_nothing" to "I didn't catch that.",
        )

    val t = SableTexts(EN)
}
