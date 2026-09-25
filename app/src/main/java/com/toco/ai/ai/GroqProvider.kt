package com.toco.ai.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Groq as a fallback brain, for when Gemini is busy, rate-limited, or blocked.
 *
 * Groq does not run Gemini — it serves open models (Llama and friends) on very
 * fast hardware. So a fallback answer sounds a little different from a Gemini
 * one, but it is a real answer instead of "the model is busy, try later". Given
 * how often Gemini returned 503, having a second brain is worth more than
 * keeping every reply on one model.
 *
 * The API is OpenAI-compatible, so this is a plain chat-completions call over
 * HttpURLConnection — no library added, same shape as GeminiProvider.
 */
class GroqProvider(
    private val apiKey: String,
    private val model: String = "llama-3.3-70b-versatile"
) : AIProvider {

    override val name = "Groq"

    override fun isConfigured(): Boolean = apiKey.isNotBlank()

    override fun ask(prompt: String, history: List<AIProvider.Turn>): AIResult {
        if (!isConfigured()) {
            return AIResult.NotConfigured("No Groq key set.")
        }

        return try {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }

            connection.outputStream.use { it.write(body(prompt, history).toByteArray()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            connection.disconnect()

            when {
                code == 401 || code == 403 -> AIResult.NotConfigured("Groq rejected the key.")
                code == 429 -> AIResult.Failed("Groq rate limit reached.")
                code !in 200..299 -> AIResult.Failed("Groq error $code.")
                else -> parse(raw)
            }
        } catch (e: Exception) {
            AIResult.Failed("Couldn't reach Groq: ${e.message}")
        }
    }

    private fun body(prompt: String, history: List<AIProvider.Turn>): String {
        val messages = JSONArray()

        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", SYSTEM_PROMPT)
        )

        history.takeLast(MAX_HISTORY).forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", if (turn.fromUser) "user" else "assistant")
                    .put("content", turn.text)
            )
        }

        messages.put(JSONObject().put("role", "user").put("content", prompt))

        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 500)
            .toString()
    }

    private fun parse(raw: String): AIResult {
        return try {
            val choices = JSONObject(raw).optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return AIResult.Failed("Groq returned nothing.")
            }

            val text = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content", "")
                ?.trim()
                .orEmpty()

            if (text.isEmpty()) AIResult.Failed("Groq returned an empty reply.")
            else AIResult.Ok(text)
        } catch (e: Exception) {
            AIResult.Failed("Couldn't read Groq's reply.")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val MAX_HISTORY = 10

        const val SYSTEM_PROMPT =
            "You are TOCO, a voice assistant on the user's Android phone. Keep replies " +
                "short and conversational, usually one or two sentences, since they may be " +
                "read aloud. No markdown, no bullet points, no emoji. If asked to control " +
                "the phone in a way you cannot, say plainly what Android does not allow."
    }
}
