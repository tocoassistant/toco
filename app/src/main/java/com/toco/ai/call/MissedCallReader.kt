package com.toco.ai.call

import android.Manifest
import android.content.Context
import android.provider.CallLog
import com.toco.ai.util.Permissions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads missed calls from Android's call log.
 *
 * Reading the log after the fact, rather than watching call state live, is the
 * deliberate choice here: the log is the system's own record, so nothing is
 * missed if TOCO was killed, restarted, or asleep while the phone rang. Live
 * state tracking would lose exactly the calls this feature exists to report.
 */
object MissedCallReader {

    /** Missed calls newer than [since] (epoch millis), oldest first. */
    fun since(context: Context, since: Long): List<MissedCall> {
        if (!Permissions.has(context, Manifest.permission.READ_CALL_LOG)) return emptyList()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE
        )

        val cursor = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                CallLog.Calls.TYPE + " = ? AND " + CallLog.Calls.DATE + " > ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), since.toString()),
                CallLog.Calls.DATE + " ASC"
            )
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val calls = mutableListOf<MissedCall>()

        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(0) ?: ""
                val cached = it.getString(1)
                val time = it.getLong(2)

                calls += MissedCall(
                    number = number,
                    // CACHED_NAME is the contact name Android already resolved.
                    name = if (cached.isNullOrBlank()) null else cached,
                    time = time
                )
            }
        }

        return calls
    }

    /** A single log entry, whatever its type. */
    data class Entry(val number: String, val name: String?, val time: Long, val missed: Boolean) {
        fun label(): String = name ?: if (number.isBlank()) "an unknown number" else number
    }

    /**
     * Most recent calls of any kind, newest first.
     *
     * Used by "call last caller" and "show recent calls". Reads the same
     * system log as the missed-call path so the two can never disagree.
     */
    fun recent(context: Context, limit: Int = 10, missedOnly: Boolean = false): List<Entry> {
        if (!Permissions.has(context, Manifest.permission.READ_CALL_LOG)) return emptyList()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE
        )

        val selection = if (missedOnly) CallLog.Calls.TYPE + " = ?" else null
        val args = if (missedOnly) arrayOf(CallLog.Calls.MISSED_TYPE.toString()) else null

        val cursor = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                args,
                CallLog.Calls.DATE + " DESC"
            )
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val entries = mutableListOf<Entry>()

        cursor.use {
            while (it.moveToNext() && entries.size < limit) {
                val number = it.getString(0) ?: ""
                val cached = it.getString(1)
                entries += Entry(
                    number = number,
                    name = if (cached.isNullOrBlank()) null else cached,
                    time = it.getLong(2),
                    missed = it.getInt(3) == CallLog.Calls.MISSED_TYPE
                )
            }
        }

        return entries
    }

    /**
     * What TOCO says out loud.
     *
     * One call gets the full detail the user asked for. Several get a summary,
     * because reading out six numbers is worse than useless.
     */
    fun announcement(calls: List<MissedCall>): String {
        if (calls.isEmpty()) return ""

        if (calls.size == 1) {
            val call = calls.first()
            return "You have a missed call from ${call.label()} at ${clock(call.time)}."
        }

        val names = calls.map { it.label() }.distinct()

        return if (names.size <= 3) {
            "You have ${calls.size} missed calls, from ${joinNaturally(names)}."
        } else {
            "You have ${calls.size} missed calls, including from " +
                "${joinNaturally(names.take(2))} and ${names.size - 2} others."
        }
    }

    /** Short line for the notification, where full dates are readable. */
    fun notificationText(calls: List<MissedCall>): String =
        calls.joinToString("\n") { "${it.label()} — ${stamp(it.time)}" }

    private fun joinNaturally(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private fun clock(time: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))

    private fun stamp(time: Long): String =
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(time))
}
