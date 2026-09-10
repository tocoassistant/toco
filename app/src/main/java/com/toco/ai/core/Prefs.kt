package com.toco.ai.core

import android.content.Context
import android.content.SharedPreferences

/** Small typed wrapper so SharedPreferences keys live in exactly one place. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("toco_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString(KEY_NAME, "Wind") ?: "Wind"
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    var countryCode: String
        get() = sp.getString(KEY_CC, "92") ?: "92"
        set(value) = sp.edit().putString(KEY_CC, value).apply()

    /** Which entry in Voices.options is selected. */
    var voiceId: String
        get() = sp.getString(KEY_VOICE_ID, "default") ?: "default"
        set(value) = sp.edit().putString(KEY_VOICE_ID, value).apply()

    var voiceReplies: Boolean
        get() = sp.getBoolean(KEY_VOICE, true)
        set(value) = sp.edit().putBoolean(KEY_VOICE, value).apply()

    /** Background wake-word listening. Off by default — it costs battery. */
    var wakeEnabled: Boolean
        get() = sp.getBoolean(KEY_WAKE, false)
        set(value) = sp.edit().putBoolean(KEY_WAKE, value).apply()

    /** Comma-separated wake phrases, editable by the user. */
    var wakeWords: String
        get() = sp.getString(KEY_WAKE_WORDS, DEFAULT_WAKE) ?: DEFAULT_WAKE
        set(value) = sp.edit().putString(KEY_WAKE_WORDS, value).apply()

    /**
     * Wake phrases, normalised. Longest first so "hey toco" is matched before
     * plain "toco" and the leftover text isn't mangled.
     */
    fun wakePhrases(): List<String> =
        wakeWords.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }

    private companion object {
        const val KEY_NAME = "user_name"
        const val KEY_CC = "country_code"
        const val KEY_VOICE = "voice_replies"
        const val KEY_VOICE_ID = "voice_id"
        const val KEY_WAKE = "wake_enabled"
        const val KEY_WAKE_WORDS = "wake_words"
        const val DEFAULT_WAKE = "hey toco, toco, hi toco, assistant, ok toco"
    }
}
