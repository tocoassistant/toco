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

    var voiceReplies: Boolean
        get() = sp.getBoolean(KEY_VOICE, true)
        set(value) = sp.edit().putBoolean(KEY_VOICE, value).apply()

    private companion object {
        const val KEY_NAME = "user_name"
        const val KEY_CC = "country_code"
        const val KEY_VOICE = "voice_replies"
    }
}
