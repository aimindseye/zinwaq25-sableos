package org.vaachak.reader.core.data.repository

import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.first
import org.vaachak.reader.core.data.remote.CloudflareAiApi
import org.vaachak.reader.core.domain.model.AiImageRequest
import java.net.SocketTimeoutException
import javax.inject.Inject

class AiRepository @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val cloudflareApi: CloudflareAiApi // Injected from AiModule
) {

    // --- GEMINI SETUP ---
    private suspend fun getGeminiModel(): GenerativeModel {
        val key = settingsRepo.geminiKey.first()
        if (key.isBlank()) throw Exception("Gemini API Key is missing.")
        // Using updated flash model
        return GenerativeModel(modelName = "gemini-2.0-flash", apiKey = key)
    }

    // --- CLOUDFLARE IMAGE GENERATION ---
    suspend fun visualizeText(prompt: String): String {
        return try {
            // 1. Get Dynamic URL & Token from Settings
            val url = settingsRepo.cfUrl.first().trim()
            val token = settingsRepo.cfToken.first().trim()

            if (url.isEmpty() || token.isEmpty()) {
                return "Error: AI Settings missing. Please configure URL/Token in Settings."
            }

            // 2. Call API (Retrofit handles the dynamic URL override)
            val response = cloudflareApi.generateImage(
                fullUrl = url,
                request = AiImageRequest(prompt = prompt),
                token = "Bearer $token"
            )

            Log.d("Visualize-Debug", "Prompt: $prompt, Code: ${response.code()}")

            // 3. Handle Raw Bytes -> Base64
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    // Convert raw bytes to Base64 for the UI to render
                    Base64.encodeToString(bytes, Base64.DEFAULT)
                } else {
                    "Error: Empty image received from server."
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                "Cloudflare Error ${response.code()}: $errorBody"
            }
        } catch (e: SocketTimeoutException) {
            "Error: Timeout. The image generation took too long."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }

    // --- TEXT GENERATION HELPERS ---

    suspend fun explainContext(selectedText: String): String {
        return try {
            val prompt = "Provide a simple, 2-sentence explanation for: $selectedText"
            getGeminiModel().generateContent(prompt).text ?: "No explanation."
        } catch (e: Exception) { "Error: ${e.localizedMessage}" }
    }

    suspend fun whoIsThis(selectedText: String, bookTitle: String, bookAuthor: String): String {
        return try {
            val prompt = "Reading '$bookTitle' by $bookAuthor. Identify character '$selectedText' without spoilers."
            getGeminiModel().generateContent(prompt).text ?: "No identification."
        } catch (e: Exception) { "Error: ${e.localizedMessage}" }
    }

    suspend fun generateRecap(bookTitle: String, highlightsContext: String, currentPageText: String): String {
        return try {
            val prompt = """
                I am returning to read '$bookTitle'. 
                Based on my previous highlights: "$highlightsContext"
                And current page: "$currentPageText"
                Provide a quick 3-sentence recap and 'Who's Who' for this scene. Avoid future spoilers.
            """.trimIndent()
            getGeminiModel().generateContent(prompt).text ?: "Unable to generate recap."
        } catch (e: Exception) { "Error: ${e.localizedMessage}" }
    }

    suspend fun getRecallSummary(bookTitle: String, highlightsContext: String): String {
        return try {
            val prompt = """
                I am returning to read '$bookTitle'. 
                Based on highlights: "$highlightsContext"
                Provide a quick 3-sentence recap. Avoid future spoilers.
            """.trimIndent()
            getGeminiModel().generateContent(prompt).text ?: "Unable to generate recap."
        } catch (e: Exception) { "Error: ${e.localizedMessage}" }
    }
}