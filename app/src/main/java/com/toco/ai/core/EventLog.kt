package com.toco.ai.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records what actually happens at runtime, on the device, with timestamps.
 *
 * This exists because the hardest bugs in TOCO have all been invisible from
 * the outside: a screening service that is never invoked, an overlay window
 * silently refused, a speech engine that dies and stops speaking. Every one of
 * those looks identical to the user — nothing happens — and none of them can
 * be reproduced anywhere except on the phone itself.
 *
 * So instead of guessing, the app writes a timeline. Entries are short, capped,
 * and stored in the app's own files directory; nothing is uploaded anywhere.
 *
 * Deliberately cheap: a single append to an in-memory list, flushed to disk on
 * a background thread, so instrumenting a hot path costs nothing worth
 * measuring.
 */
object EventLog {

    private const val FILE = "toco-events.log"
    private const val MAX_LINES = 400

    private val lines = mutableListOf<String>()
    private var loaded = false

    /** Record an event. [tag] groups related events, [detail] carries the facts. */
    @Synchronized
    fun log(context: Context, tag: String, detail: String) {
        val app = context.applicationContext
        ensureLoaded(app)

        val line = stamp() + "  " + tag.padEnd(12) + "  " + detail
        lines += line

        // Keep the newest; an unbounded log on a phone is its own bug.
        while (lines.size > MAX_LINES) lines.removeAt(0)

        Thread { flush(app) }.start()
    }

    @Synchronized
    fun entries(context: Context): List<String> {
        ensureLoaded(context.applicationContext)
        return lines.reversed()
    }

    @Synchronized
    fun asText(context: Context): String {
        ensureLoaded(context.applicationContext)
        return lines.joinToString("\n")
    }

    @Synchronized
    fun clear(context: Context) {
        lines.clear()
        try {
            file(context.applicationContext).delete()
        } catch (e: Exception) {
            // Nothing to do; the in-memory copy is already empty.
        }
    }

    private fun ensureLoaded(app: Context) {
        if (loaded) return
        loaded = true

        try {
            val f = file(app)
            if (f.exists()) {
                lines += f.readLines().takeLast(MAX_LINES)
            }
        } catch (e: Exception) {
            // Unreadable log is not worth failing over.
        }
    }

    private fun flush(app: Context) {
        try {
            synchronized(this) {
                file(app).writeText(lines.joinToString("\n"))
            }
        } catch (e: Exception) {
            // Out of space or no permission; the in-memory log still works.
        }
    }

    private fun file(app: Context) = File(app.filesDir, FILE)

    private fun stamp(): String =
        SimpleDateFormat("MMM d HH:mm:ss", Locale.US).format(Date())
}
