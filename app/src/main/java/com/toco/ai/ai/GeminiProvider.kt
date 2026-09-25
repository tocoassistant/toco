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
    /**
     * Tried in order. Google renames and retires model ids, and a 404 on one
     * name shouldn't look like "Gemini is broken" — so TOCO falls through to
     * the next instead of failing outright.
     */
    private val models: List<String> = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-flash-latest"
    )
) : AIProvider {

    override val name = "Gemini"

    override fun isConfigured(): Boolean = apiKey.isNotBlank()

    override fun ask(prompt: String, history: List<AIProvider.Turn>): AIResult {
        if (!isConfigured()) {
            return AIResult.NotConfigured(
                "No Gemini key in this build. Add GEMINI_API_KEY as a GitHub " +
                    "repository secret, or to local.properties if building on device."
            )
        }

        var last: AIResult = AIResult.Failed("No model responded.")

        for (model in models) {
            val result = call(model, prompt, history)
            // A model-name problem is worth retrying; anything else is final.
            if (result is AIResult.Failed && result.message.contains("404")) {
                last = result
                continue
            }
            return result
        }

        return last
    }

    /**
     * Wraps one model call with a couple of retries.
     *
     * 503 ("high demand") and 429 (rate limit) are almost always momentary on
     * Google's side — the key is fine, the server is just busy. Retrying twice
     * with a growing pause turns most of those from a visible failure into a
     * slight delay, instead of making the user repeat themselves.
     */
    private fun call(
        model: String,
        prompt: String,
        history: List<AIProvider.Turn>
    ): AIResult {
        var attempt = 0
        while (true) {
            val result = callOnce(model, prompt, history)

            val busy = result is AIResult.Failed &&
                (result.message.contains("503") || result.message.contains("rate limit"))

            if (!busy || attempt >= MAX_RETRIES) return result

            attempt++
            try {
                Thread.sleep(RETRY_BASE_MS * attempt)
            } catch (e: InterruptedException) {
                return result
            }
        }
    }

    private fun callOnce(
        model: String,
        prompt: String,
        history: List<AIProvider.Turn>
    ): AIResult {
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
                    "Gemini rejected the key: " + reason(raw)
                )
                code == 404 -> AIResult.Failed("404: model $model not available.")
                code == 429 -> AIResult.Failed("rate limit - busy")
                code == 500 || code == 503 -> AIResult.Failed(
                    "Gemini error 503: the model is busy right now."
                )
                code !in 200..299 -> AIResult.Failed("Gemini error $code: " + reason(raw))
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

    /** Pulls the human-readable message out of an API error body. */
    private fun reason(raw: String): String =
        try {
            JSONObject(raw).optJSONObject("error")?.optString("message", "") ?: ""
        } catch (e: Exception) {
            ""
        }.ifBlank { "no details returned" }.take(160)

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
        const val MAX_RETRIES = 2
        const val RETRY_BASE_MS = 1200L

        const val SYSTEM_PROMPT =
            "You are TOCO, a voice assistant running on the user's Android phone. " +
                "Keep replies short and conversational — usually one or two sentences, " +
                "since they may be read aloud. Do not use markdown, bullet points or " +
                "emoji. If the user asks you to control the phone in a way you cannot, " +
                "say plainly what Android does not allow instead of pretending it worked."
    }
}
