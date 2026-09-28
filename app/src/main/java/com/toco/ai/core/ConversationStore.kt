package com.toco.ai.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lightweight on-device conversation timeline. Supabase sync can mirror these records later. */
object ConversationStore {
    data class Message(val role: String, val text: String, val at: Long)

    private const val PREFS = "toco_conversation"
    private const val KEY = "messages"
    private const val MAX = 120

    @Synchronized
    fun add(context: Context, role: String, text: String) {
        if (text.isBlank()) return
        val all = readArray(context)
        all.put(JSONObject().put("role", role).put("text", text.trim()).put("at", System.currentTimeMillis()))
        while (all.length() > MAX) {
            val trimmed = JSONArray()
            for (i in 1 until all.length()) trimmed.put(all.get(i))
            saveArray(context, trimmed)
            return
        }
        saveArray(context, all)
    }

    @Synchronized
    fun messages(context: Context): List<Message> {
        val a = readArray(context)
        val out = ArrayList<Message>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += Message(o.optString("role", "assistant"), o.optString("text"), o.optLong("at"))
        }
        return out
    }

    fun summary(context: Context, limit: Int = 8): String {
        val items = messages(context).takeLast(limit)
        if (items.isEmpty()) return "No meaningful conversation saved on this device yet."
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return items.reversed().joinToString("\n\n") {
            val who = if (it.role == "user") "You" else "TOCO"
            "$who · ${fmt.format(Date(it.at))}\n${it.text}"
        }
    }

    private fun readArray(context: Context): JSONArray = try {
        JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun saveArray(context: Context, a: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }
}
