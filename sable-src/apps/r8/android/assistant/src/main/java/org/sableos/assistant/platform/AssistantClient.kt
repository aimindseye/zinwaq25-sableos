package org.sableos.assistant.platform

import org.sableos.assistant.core.ChatProtocol
import org.sableos.assistant.core.ChatReply
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI

/** What a request ended in, before the screen words it. */
sealed interface CallResult {
    data class Ok(
        val body: String,
    ) : CallResult

    data class HttpError(
        val message: String,
    ) : CallResult

    data object Unreachable : CallResult

    data object Timeout : CallResult
}

/**
 * Plain HTTP to an OpenAI-style server: POST /chat/completions and GET /models. Blocking; call it
 * off the main thread. Only the owner's question, the recent turns and the system prompt are sent.
 */
object AssistantClient {
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 120_000
    private const val HTTP_ERROR = 400

    fun chat(
        base: String,
        apiKey: String,
        body: String,
    ): CallResult = call("$base/chat/completions", apiKey, body)

    fun models(
        base: String,
        apiKey: String,
    ): CallResult = call("$base/models", apiKey, null)

    private fun call(
        url: String,
        apiKey: String,
        body: String?,
    ): CallResult {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_MS
            connection.readTimeout = READ_MS
            connection.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= HTTP_ERROR) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code >= HTTP_ERROR) {
                val reply = ChatProtocol.reply(text)
                CallResult.HttpError(if (reply is ChatReply.Failed) "$code ${reply.message}" else "$code")
            } else {
                CallResult.Ok(text)
            }
        } catch (_: SocketTimeoutException) {
            CallResult.Timeout
        } catch (_: IOException) {
            CallResult.Unreachable
        } finally {
            connection.disconnect()
        }
    }
}
