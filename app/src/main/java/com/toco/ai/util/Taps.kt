package com.toco.ai.util

/**
 * Stops a rapid burst of taps from turning into a burst of actions.
 *
 * Android delivers every single touch, so a control that starts something —
 * a command, a call, a dialog, an overlay — will start it once per tap. Six
 * impatient presses on the send button used to queue six commands, six spoken
 * replies and, where the action opened something, six windows. The app looked
 * frozen while it worked through a backlog the user never meant to create.
 *
 * The rule here is deliberately simple: the first tap wins and the rest are
 * dropped, rather than debouncing to the last tap. For an assistant the first
 * press is what the user meant; making them wait to find out whether more
 * presses are coming would add lag to every single interaction.
 */
object Taps {

    private val lastAccepted = mutableMapOf<String, Long>()

    /** Default gap. Long enough to swallow a double tap, short enough to feel instant. */
    const val DEFAULT_MS = 600L

    /** Longer, for actions that open a screen or start a call. */
    const val HEAVY_MS = 1500L

    /**
     * True if this tap should be acted on. Call it once, at the top of the
     * handler — calling it twice for one tap would consume the allowance.
     */
    @Synchronized
    fun allow(key: String, intervalMs: Long = DEFAULT_MS): Boolean {
        val now = System.currentTimeMillis()
        val last = lastAccepted[key] ?: 0L

        if (now - last < intervalMs) return false

        lastAccepted[key] = now
        return true
    }

    /**
     * Clears the record for [key], so the next tap is accepted immediately.
     * Used when an action finishes early and the control is live again.
     */
    @Synchronized
    fun reset(key: String) {
        lastAccepted.remove(key)
    }
}
