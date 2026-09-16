package com.toco.ai.skill.builtin

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText

/**
 * "set alarm for 7", "alarm at 6:30 am", "timer for 5 minutes", "wake me at 8"
 *
 * Uses the AlarmClock intents, which every clock app implements and which need
 * no permission at all. The alarm is created directly rather than just opening
 * the clock app, so one sentence finishes the job.
 */
class AlarmSkill : Skill {

    override val id = "core.alarm"
    override val name = "Alarm & Timer"
    override val priority = 80

    override fun canHandle(command: String): Boolean {
        if (CommandText.hasAnyPhrase(command, listOf("alarm", "timer"))) return true
        // "wake me at 7" reads as an alarm even without the word.
        return CommandText.hasPhrase(command, "wake") &&
            CommandText.hasAnyPhrase(command, listOf("at", "up"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = CommandText.normalize(command)

        return if (CommandText.hasPhrase(command, "timer")) {
            timer(context, c)
        } else {
            alarm(context, c)
        }
    }

    // ---------------- timer ----------------

    private fun timer(context: Context, c: String): SkillResult {
        val seconds = durationSeconds(c)
            ?: return SkillResult.Failed("How long should the timer be?")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            SkillResult.Ok("Timer set for " + spoken(seconds))
        } catch (e: Exception) {
            SkillResult.Failed("No clock app that can set timers.")
        }
    }

    private fun durationSeconds(c: String): Int? {
        val match = Regex("""(\d+)\s*(hour|hr|minute|min|second|sec)""").find(c) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null

        return when {
            match.groupValues[2].startsWith("h") -> amount * 3600
            match.groupValues[2].startsWith("m") -> amount * 60
            else -> amount
        }
    }

    private fun spoken(seconds: Int): String = when {
        seconds >= 3600 -> (seconds / 3600).toString() + " hour(s)"
        seconds >= 60 -> (seconds / 60).toString() + " minute(s)"
        else -> seconds.toString() + " seconds"
    }

    // ---------------- alarm ----------------

    private fun alarm(context: Context, c: String): SkillResult {
        val time = parseTime(c) ?: return SkillResult.Failed("What time should I set it for?")

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, time.first)
            .putExtra(AlarmClock.EXTRA_MINUTES, time.second)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            SkillResult.Ok("Alarm set for " + format(time.first, time.second))
        } catch (e: Exception) {
            SkillResult.Failed("No clock app that can set alarms.")
        }
    }

    /**
     * Handles "7", "7:30", "6 30", with or without am/pm.
     *
     * Bare hours are read as the next sensible one: "set alarm for 7" at
     * night means 7am, not a time that has already passed today.
     */
    private fun parseTime(c: String): Pair<Int, Int>? {
        val match = Regex("""(\d{1,2})[:.\s]?(\d{2})?\s*(am|pm)?""").find(c) ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val marker = match.groupValues[3]

        if (hour > 23 || minute > 59) return null

        // With am/pm, do exactly as told. Without it, take the hour at face
        // value: "alarm for 7" means seven in the morning to almost everyone,
        // and quietly shifting it by twelve hours is the kind of surprise that
        // makes someone late.
        if (marker == "pm" && hour < 12) hour += 12
        if (marker == "am" && hour == 12) hour = 0

        return hour to minute
    }

    private fun format(hour: Int, minute: Int): String {
        val display = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val suffix = if (hour < 12) "AM" else "PM"
        return String.format("%d:%02d %s", display, minute, suffix)
    }
}
