package com.toco.ai.ai

import com.toco.ai.BuildConfig

/**
 * The single place the app resolves which provider to use, and the fallback
 * order between them.
 *
 * Gemini is tried first; if it fails for a reason that a second brain could
 * answer anyway — busy, rate-limited, blocked — Groq is tried next. A failure
 * that Groq could not fix either (an empty prompt, say) is not worth a second
 * network round trip, so only the recoverable kinds fall through.
 *
 * Chat history lives here so the conversation survives switching tabs, but is
 * intentionally in-memory only: nothing is written to disk, and it clears on
 * app restart.
 */
object Ai {

    private val history = mutableListOf<AIProvider.Turn>()

    /** Primary. */
    private val gemini: AIProvider by lazy {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) AIProvider.None else GeminiProvider(key)
    }

    /** Fallback. */
    private val groq: AIProvider by lazy {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isBlank()) AIProvider.None else GroqProvider(key)
    }

    /** True if at least one brain is configured. */
    fun isReady(): Boolean = gemini.isConfigured() || groq.isConfigured()

    /** Blocking. Call from a background thread. */
    fun ask(prompt: String): AIResult {
        val turns = history.toList()

        var result = firstReady()?.ask(prompt, turns)
            ?: return AIResult.Failed("No AI provider is configured.")

        // Fall through to the backup only when the primary failed in a way the
        // backup might actually recover from, and only if a backup exists.
        if (shouldFallBack(result) && groq.isConfigured() && gemini.isConfigured()) {
            val backup = groq.ask(prompt, turns)
            if (backup is AIResult.Ok) result = backup
        }

        if (result is AIResult.Ok) {
            history += AIProvider.Turn(fromUser = true, text = prompt)
            history += AIProvider.Turn(fromUser = false, text = result.text)
            while (history.size > MAX_TURNS) history.removeAt(0)
        }

        return result
    }

    /** Gemini if it has a key, otherwise Groq, otherwise nothing. */
    private fun firstReady(): AIProvider? = when {
        gemini.isConfigured() -> gemini
        groq.isConfigured() -> groq
        else -> null
    }

    /**
     * A network/availability failure is worth retrying on the other brain; a
     * "you sent nothing" style failure is not. NotConfigured never falls
     * through — a missing key is not fixed by asking a second time.
     */
    private fun shouldFallBack(result: AIResult): Boolean =
        result is AIResult.Failed

    fun clearHistory() = history.clear()

    fun turns(): List<AIProvider.Turn> = history.toList()

    private const val MAX_TURNS = 20
}
