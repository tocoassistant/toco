package com.toco.ai.ai

import com.toco.ai.BuildConfig

/**
 * The single place the app resolves which provider to use.
 *
 * Chat history lives here so the conversation survives switching tabs, but is
 * intentionally in-memory only — nothing is written to disk, and it clears on
 * app restart.
 */
object Ai {

    private val history = mutableListOf<AIProvider.Turn>()

    val provider: AIProvider by lazy {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) AIProvider.None else GeminiProvider(key)
    }

    fun isReady(): Boolean = provider.isConfigured()

    /** Blocking. Call from a background thread. */
    fun ask(prompt: String): AIResult {
        val result = provider.ask(prompt, history.toList())

        if (result is AIResult.Ok) {
            history += AIProvider.Turn(fromUser = true, text = prompt)
            history += AIProvider.Turn(fromUser = false, text = result.text)
            while (history.size > MAX_TURNS) history.removeAt(0)
        }

        return result
    }

    fun clearHistory() = history.clear()

    fun turns(): List<AIProvider.Turn> = history.toList()

    private const val MAX_TURNS = 20
}
