package com.toco.ai.util

/**
 * Turns messy spoken input into something skills can match reliably.
 *
 * The old matching was substring soup — a skill claimed a command if the text
 * merely *contained* a word. That is why "open play store" was grabbed by the
 * media skill: it contains "play". Commands worked once and then broke as soon
 * as another skill's keyword happened to appear.
 *
 * The rule now: match on the VERB AT THE START, or on a whole word, never on a
 * substring buried anywhere in the sentence.
 */
object CommandText {

    /**
     * Everyday phrasings mapped to what the skills expect, so users can talk
     * normally instead of memorising exact syntax.
     */
    private val aliases = listOf(
        // politeness and filler that carries no meaning
        "please " to "",
        "can you " to "",
        "could you " to "",
        "would you " to "",
        "i want to " to "",
        "i need to " to "",
        "hey " to "",
        "toco " to "",

        // flashlight
        "torch" to "flashlight",
        "flash light" to "flashlight",

        // volume
        "sound up" to "volume up",
        "sound down" to "volume down",
        "louder" to "volume up",
        "quieter" to "volume down",
        "turn it up" to "volume up",
        "turn it down" to "volume down",

        // apps
        "go to " to "open ",
        "take me to " to "open ",
        "bring up " to "open ",
        "fire up " to "open ",

        // media
        "skip song" to "next song",
        "skip track" to "next song",
        "next one" to "next song",

        // calling
        "give a call to " to "call ",
        "make a call to " to "call ",
        "ring up " to "call "
    )

    /** Lowercased, de-punctuated, filler and aliases resolved. */
    fun normalize(raw: String): String {
        var text = raw.lowercase().trim()

        // Strip trailing punctuation that speech recognition often adds.
        text = text.trim('.', ',', '!', '?', ';')

        for ((from, to) in aliases) {
            if (text.startsWith(from)) {
                text = to + text.removePrefix(from)
            } else {
                text = text.replace(" $from", " $to")
            }
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    fun words(command: String): List<String> =
        normalize(command).split(" ").filter { it.isNotEmpty() }

    /** True if the command's FIRST word is one of [verbs]. */
    fun startsWithVerb(command: String, verbs: List<String>): Boolean {
        val first = words(command).firstOrNull() ?: return false
        return first in verbs
    }

    /** True if [phrase] appears as whole words, not as part of another word. */
    fun hasPhrase(command: String, phrase: String): Boolean {
        val padded = " " + normalize(command) + " "
        return padded.contains(" ${phrase.lowercase()} ")
    }

    fun hasAnyPhrase(command: String, phrases: List<String>): Boolean =
        phrases.any { hasPhrase(command, it) }

    /** Everything after the leading verb. "open youtube" -> "youtube" */
    fun afterVerb(command: String): String =
        words(command).drop(1).joinToString(" ").trim()

    /** Removes the given whole words, used to isolate a target from a command. */
    fun without(command: String, remove: List<String>): String {
        var text = " " + normalize(command) + " "
        for (word in remove) {
            text = text.replace(" ${word.lowercase()} ", " ")
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
