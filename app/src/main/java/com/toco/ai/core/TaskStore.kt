package com.toco.ai.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight, local task queue. Tasks never gain permissions beyond TOCO itself. */
object TaskStore {
    private const val PREFS = "toco_tasks"
    private const val KEY = "items"
    private const val MAX = 80

    data class Task(val id: Long, val text: String, val state: String, val createdAt: Long)

    fun add(context: Context, text: String): Task {
        val task = Task(System.currentTimeMillis(), text.trim(), "Pending", System.currentTimeMillis())
        val all = tasks(context).toMutableList()
        all.add(0, task)
        save(context, all.take(MAX))
        EventLog.log(context, "TASK", "Created: ${task.text}")
        return task
    }

    fun tasks(context: Context): List<Task> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Task(o.getLong("id"), o.getString("text"), o.optString("state", "Pending"), o.optLong("createdAt", o.getLong("id")))
        }
    } catch (_: Exception) { emptyList() }

    fun complete(context: Context, id: Long) = update(context, id, "Done")
    fun cancel(context: Context, id: Long) = update(context, id, "Cancelled")

    private fun update(context: Context, id: Long, state: String) {
        val updated = tasks(context).map { if (it.id == id) it.copy(state = state) else it }
        save(context, updated)
        updated.firstOrNull { it.id == id }?.let { EventLog.log(context, "TASK", "$state: ${it.text}") }
    }

    private fun save(context: Context, tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { t -> arr.put(JSONObject().put("id", t.id).put("text", t.text).put("state", t.state).put("createdAt", t.createdAt)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
