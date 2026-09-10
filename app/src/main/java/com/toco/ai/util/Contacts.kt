package com.toco.ai.util

import android.Manifest
import android.content.Context
import android.provider.ContactsContract

/**
 * Resolves a spoken name to a phone number.
 *
 * This is what makes "call mom" work instead of only "call 03001234567".
 * Requires READ_CONTACTS; without it TOCO says so plainly rather than
 * silently failing to match the command.
 */
object Contacts {

    data class Match(val name: String, val number: String)

    /**
     * Looks up [query] against contact display names.
     *
     * Uses a LIKE match, so "mom" finds "Mom" and "call ammi" finds "Ammi Jan".
     * Returns the first match; ambiguity is resolved by asking the user rather
     * than guessing, which is handled a level up.
     */
    fun findByName(context: Context, query: String): Match? {
        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) return null

        val name = query.trim()
        if (name.isEmpty()) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$name%"),
            null
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            val display = it.getString(0) ?: name
            val number = it.getString(1) ?: return null
            return Match(display, number)
        }
    }

    /**
     * Strips the trigger verb and filler words so what remains is a plausible
     * contact name. "please call my mom now" -> "mom"
     */
    fun nameFrom(command: String, triggers: List<String>): String {
        var text = command.lowercase().trim()

        // Drop a leading trigger word.
        for (trigger in triggers) {
            if (text.startsWith("$trigger ")) {
                text = text.removePrefix("$trigger ").trim()
                break
            }
            if (text == trigger) return ""
        }

        val filler = listOf(
            "please", "can you", "could you", "for me", "now",
            "my", "the", "a", "to", "up", "on", "number", "phone", "contact"
        )
        for (word in filler) {
            text = text.removePrefix("$word ").removeSuffix(" $word").trim()
        }

        // Anything after "saying"/"and say" belongs to a message, not the name.
        for (cut in listOf(" saying ", " and say ", " that ", " message ")) {
            val at = text.indexOf(cut)
            if (at > 0) text = text.substring(0, at).trim()
        }

        return text.trim()
    }
}
