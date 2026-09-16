package com.toco.ai.skill.builtin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import com.toco.ai.call.MissedCallReader
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText
import com.toco.ai.util.Permissions
import com.toco.ai.util.PhoneNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "who called me", "how many missed calls", "call last caller",
 * "call back missed caller", "show recent calls", "open call history"
 *
 * Everything here reads the system call log, so it agrees with the phone's own
 * dialer rather than keeping a second record that could drift out of step.
 */
class CallHistorySkill : Skill {

    override val id = "core.call_history"
    override val name = "Call History"
    override val priority = 93

    override fun canHandle(command: String): Boolean {
        val c = CommandText.normalize(command)

        if (CommandText.hasAnyPhrase(command, listOf("call history", "call log"))) return true
        if (c.startsWith("who called")) return true
        if (c.contains("missed call")) return true
        if (CommandText.hasAnyPhrase(command, listOf("recent calls", "last caller", "last call"))) {
            return true
        }

        // "call back", "call last caller", "call the last missed call"
        return CommandText.startsWithVerb(command, listOf("call", "callback", "redial")) &&
            CommandText.hasAnyPhrase(command, listOf("back", "last", "recent", "missed"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        if (!Permissions.has(context, Manifest.permission.READ_CALL_LOG)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.READ_CALL_LOG,
                "I need call log access to answer that."
            )
        }

        val c = CommandText.normalize(command)

        if (CommandText.hasAnyPhrase(command, listOf("call history", "call log", "open"))) {
            return openHistory(context)
        }

        val missedOnly = c.contains("missed")

        // Questions first, because "how many missed calls" also contains
        // "missed call" and must not be treated as a request to dial.
        if (c.startsWith("who called") || c.contains("how many") ||
            c.startsWith("show") || c.contains("what calls")
        ) {
            return report(context, missedOnly)
        }

        return callBack(context, missedOnly)
    }

    private fun report(context: Context, missedOnly: Boolean): SkillResult {
        val entries = MissedCallReader.recent(context, 5, missedOnly)

        if (entries.isEmpty()) {
            return SkillResult.Ok(
                if (missedOnly) "No missed calls." else "No calls in your log."
            )
        }

        val names = entries.map { it.label() }.distinct()
        val heading = if (missedOnly) {
            entries.size.toString() + " missed: "
        } else {
            "Recent calls: "
        }

        return SkillResult.Ok(
            heading + names.joinToString(", ") + " \u00b7 last at " + clock(entries.first().time)
        )
    }

    private fun callBack(context: Context, missedOnly: Boolean): SkillResult {
        val entry = MissedCallReader.recent(context, 1, missedOnly).firstOrNull()
            ?: return SkillResult.Ok(
                if (missedOnly) "No missed calls to return." else "No recent calls."
            )

        if (entry.number.isBlank()) {
            return SkillResult.Failed("That caller's number was withheld.")
        }

        val number = PhoneNumbers.toDialable(entry.number)
        val direct = Permissions.has(context, Manifest.permission.CALL_PHONE)
        val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL

        return try {
            context.startActivity(
                Intent(action, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            SkillResult.Ok(
                (if (direct) "Calling " else "Dialler ready for ") + entry.label()
            )
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't start the call.")
        }
    }

    private fun openHistory(context: Context): SkillResult = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setType(CallLog.Calls.CONTENT_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        SkillResult.Ok("Opening call history")
    } catch (e: Exception) {
        SkillResult.Failed("No app can show the call history.")
    }

    private fun clock(time: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))
}
