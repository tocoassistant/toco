package com.toco.ai.ai

/**
 * Abstraction over whichever model TOCO talks to.
 *
 * Deliberately not tied to Gemini: the UI and the engine only see this
 * interface, so swapping or adding a provider touches nothing else.
 */
interface AIProvider {

    val name: String

    /** True when a key is configured and the provider can actually be called. */
    fun isConfigured(): Boolean

    /**
     * Blocking call — must not run on the main thread.
     * [history] is the prior turns, oldest first.
     */
    fun ask(prompt: String, history: List<Turn> = emptyList()): AIResult

    data class Turn(val fromUser: Boolean, val text: String)

    /** No provider configured. Says so honestly rather than failing silently. */
    object None : AIProvider {
        override val name = "None"
        override fun isConfigured() = false
        override fun ask(prompt: String, history: List<Turn>): AIResult =
            AIResult.Failed("No AI provider is configured.")
    }
}
