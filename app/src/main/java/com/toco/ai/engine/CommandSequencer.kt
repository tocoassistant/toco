package com.toco.ai.engine

import android.content.Context
import com.toco.ai.skill.SkillRegistry
import com.toco.ai.skill.SkillResult

/**
 * Runs several commands from one sentence, in order.
 *
 *   "open youtube and search for lofi then turn the volume up"
 *      -> ["open youtube", "search youtube for lofi", "turn the volume up"]
 *
 * Splitting is deliberately conservative. A phrase is only treated as a new
 * step if what follows actually looks like a command TOCO can run, because
 * "call mom and tell her I'm late" must stay one step — splitting on every
 * "and" would mangle message bodies and contact names.
 *
 * Steps run sequentially with a short gap so each app has time to come to the
 * foreground. If a step fails the sequence stops there: continuing after a
 * failure produces nonsense, like searching in an app that never opened.
 */
class CommandSequencer(private val engine: CommandEngine) {

    data class Step(val command: String, val result: SkillResult)

    /** Splits [raw] into runnable steps; a single-element list means no chaining. */
    fun split(raw: String): List<String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        // Anything after these belongs to a message body, never a new command.
        val protected = listOf(" saying ", " tell ", " say that ", " message that ")
        if (protected.any { text.lowercase().contains(it) }) return listOf(text)

        val parts = mutableListOf<String>()
        var current = StringBuilder()
        val words = text.split(" ")
        var i = 0

        while (i < words.size) {
            val word = words[i].lowercase().trim(',')

            val isSeparator = word == "then" || word == "and" ||
                words[i].endsWith(",") || word == "also" || word == "after"

            if (isSeparator && current.isNotEmpty()) {
                // Only break if the remainder is itself a recognisable command.
                val rest = words.drop(i + 1).joinToString(" ").trim()
                if (rest.isNotEmpty() && startsNewCommand(rest)) {
                    if (words[i].endsWith(",") && word !in setOf("then", "and", "also", "after")) {
                        current.append(" ").append(words[i].trimEnd(','))
                    }
                    parts += current.toString().trim()
                    current = StringBuilder()
                    i++
                    continue
                }
            }

            if (current.isNotEmpty()) current.append(" ")
            current.append(words[i])
            i++
        }

        if (current.isNotEmpty()) parts += current.toString().trim()

        return parts
            .map { it.trim().trim(',', '.', ';').trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text) }
    }

    /**
     * True when [text] reads as its own command — either a module claims it, or
     * it opens with a verb TOCO knows. The verb check matters because a step
     * like "search for lofi" needs context from the previous step to resolve.
     */
    private fun startsNewCommand(text: String): Boolean {
        if (SkillRegistry.resolve(text) != null) return true

        val verbs = listOf(
            "open", "launch", "start", "search", "find", "look",
            "play", "pause", "next", "previous", "turn", "set",
            "increase", "decrease", "mute", "call", "dial", "whatsapp",
            "message", "close", "go"
        )
        val first = text.lowercase().split(" ").firstOrNull() ?: return false
        return first in verbs
    }

    /**
     * Runs each step in order, stopping at the first failure.
     * BLOCKING — sleeps between steps, so keep it off the main thread.
     * [onStep] reports progress so the UI can show the running list.
     */
    fun run(
        context: Context,
        raw: String,
        onStep: (index: Int, total: Int, command: String) -> Unit = { _, _, _ -> },
        onDone: (List<Step>) -> Unit = {}
    ) {
        val commands = split(raw)
        val done = mutableListOf<Step>()

        commands.forEachIndexed { index, command ->
            onStep(index, commands.size, command)

            // Give the previous app a moment to actually be in front.
            if (index > 0) Thread.sleep(STEP_GAP_MS)

            val result = engine.handle(context, resolveStep(command, commands, index))
            done += Step(command, result)

            if (result !is SkillResult.Ok) {
                onDone(done)
                return
            }
        }

        onDone(done)
    }

    /**
     * Carries context forward. "open youtube, search for lofi" — the second
     * step has no app name, so the app from the previous step is folded in,
     * turning it into something SearchSkill can actually resolve.
     */
    private fun resolveStep(command: String, all: List<String>, index: Int): String {
        if (index == 0) return command

        val c = command.lowercase()
        val needsApp = (c.startsWith("search") || c.startsWith("find") ||
            c.startsWith("look")) && !c.contains(" on ") && !c.contains(" in ")
        if (!needsApp) return command

        val previous = all[index - 1].lowercase()
        val app = listOf("youtube", "google", "maps", "amazon", "spotify", "play store")
            .firstOrNull { previous.contains(it) }
            ?: return command

        return "$command on $app"
    }

    private companion object {
        const val STEP_GAP_MS = 900L
    }
}
