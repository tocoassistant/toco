package com.toco.ai.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini via the REST API, using HttpURLConnection so no HTTP library is added.
 *
 * The key comes from BuildConfig (populated from local.properties, which is
 * git-ignored). Worth being clear-eyed: a key compiled into an APK is
 * extractable by anyone who unzips it. Fine for a personal build; a shared
 * build needs a backend proxy holding the key instead.
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash"
) : AIProvider {

    override val name = "Gemini"

    override fun isConfigured(): Boolean = apiKey.isNotBlank()

    override fun ask(prompt: String, history: List<AIProvider.Turn>): AIResult {
        if (!isConfigured()) {
            return AIResult.NotConfigured(
                "No Gemini key. Add GEMINI_API_KEY to local.properties and rebuild."
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "$model:generateContent?key=$apiKey"

        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
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
                code == 400 || code == 403 -> AIResult.NotConfigured(
                    "Gemini rejected the key. Check GEMINI_API_KEY."
                )
                code == 429 -> AIResult.Failed("Gemini rate limit reached. Try again shortly.")
                code !in 200..299 -> AIResult.Failed("Gemini error $code.")
                else -> parse(raw)
            }
        } catch (e: Exception) {
            AIResult.Failed("Couldn't reach Gemini: ${e.message}")
        }
    }

    private fun body(prompt: String, history: List<AIProvider.Turn>): String {
        val contents = JSONArray()

        history.takeLast(MAX_HISTORY).forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            )
        }

        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        )

        return JSONObject()
            .put("contents", contents)
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 500)
            )
            .toString()
    }

    private fun parse(raw: String): AIResult {
        return try {
            val candidates = JSONObject(raw).optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return AIResult.Failed("Gemini returned nothing.")
            }

            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")

            val text = buildString {
                for (i in 0 until (parts?.length() ?: 0)) {
                    append(parts!!.getJSONObject(i).optString("text", ""))
                }
            }.trim()

            if (text.isEmpty()) AIResult.Failed("Gemini returned an empty reply.")
            else AIResult.Ok(text)
        } catch (e: Exception) {
            AIResult.Failed("Couldn't read Gemini's reply.")
        }
    }

    private companion object {
        const val MAX_HISTORY = 10

        const val SYSTEM_PROMPT =
            "You are TOCO, a voice assistant running on the user's Android phone. " +
                "Keep replies short and conversational — usually one or two sentences, " +
                "since they may be read aloud. Do not use markdown, bullet points or " +
                "emoji. If the user asks you to control the phone in a way you cannot, " +
                "say plainly what Android does not allow instead of pretending it worked."
    }
}
