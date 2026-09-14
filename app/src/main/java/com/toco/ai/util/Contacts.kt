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

    /** Beyond this many, a spoken chooser stops being useful. */
    private const val MAX_MATCHES = 6


    data class Match(val name: String, val number: String)

    /**
     * Looks up [query] against contact display names.
     *
     * Uses a LIKE match, so "mom" finds "Mom" and "call ammi" finds "Ammi Jan".
     * Returns the first match; ambiguity is resolved by asking the user rather
     * than guessing, which is handled a level up.
     */
    fun findByName(context: Context, query: String): Match? =
        findAll(context, query).firstOrNull()

    /**
     * Every distinct number saved under a matching name.
     *
     * Returning a list rather than the first hit is what lets TOCO ask which
     * Rohim you meant. Picking silently was wrong: it would call the wrong
     * person with no warning, which is worse than a moment of friction.
     *
     * Numbers are de-duplicated because one contact often has the same number
     * stored twice (mobile and WhatsApp), and offering an identical choice
     * twice is noise.
     */
    fun findAll(context: Context, query: String): List<Match> {
        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) return emptyList()

        val name = query.trim()
        if (name.isEmpty()) return emptyList()

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
        ) ?: return emptyList()

        val matches = mutableListOf<Match>()
        val seen = mutableSetOf<String>()

        cursor.use {
            while (it.moveToNext()) {
                val display = it.getString(0) ?: name
                val number = it.getString(1) ?: continue

                // Compare on digits only: "0300 123" and "0300123" are one number.
                val key = number.filter { c -> c.isDigit() }
                if (key.isEmpty() || !seen.add(key)) continue

                matches += Match(display, number)
                if (matches.size >= MAX_MATCHES) break
            }
        }

        // Rank, then keep ONLY the best tier that exists.
        //
        // A plain LIKE '%ma%' is why asking for "Ma" offered Mama, Bablu Mama
        // and Rakib Mama. Those are different people. If a contact is actually
        // called "Ma", the ones that merely contain those letters are noise and
        // are dropped rather than listed.
        val best = matches.groupBy { tier(it.name, name) }.minByOrNull { it.key }
        return best?.value ?: emptyList()
    }

    /**
     * 0 = the whole name matches
     * 1 = a word in the name starts with the query ("Bablu Mama" for "mama")
     * 2 = the letters appear somewhere ("Mama" for "ma")
     */
    private fun tier(contactName: String, query: String): Int {
        val name = contactName.lowercase().trim()
        val q = query.lowercase().trim()

        if (name == q) return 0

        val words = name.split(" ", ",", "[", "]", "(", ")", "-")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (words.any { it == q }) return 0
        if (words.any { it.startsWith(q) }) return 1
        return 2
    }

    /**
     * Saved name for a number, or null if it is not in contacts.
     *
     * Matches on the last 9 digits so that +8801327110515, 01327110515 and
     * 1327110515 all resolve to the same person — callers arrive in whichever
     * format the network hands over, which is rarely how it was saved.
     */
    fun findByNumber(context: Context, number: String): String? {
        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) return null

        val digits = number.filter { it.isDigit() }
        if (digits.length < 6) return null
        val tail = digits.takeLast(9)

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            null
        } ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val saved = it.getString(1) ?: continue
                val savedDigits = saved.filter { c -> c.isDigit() }
                if (savedDigits.length >= 6 && savedDigits.takeLast(9) == tail) {
                    return it.getString(0)
                }
            }
        }

        return null
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
