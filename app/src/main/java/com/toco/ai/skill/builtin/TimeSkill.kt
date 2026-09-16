package com.toco.ai.skill.builtin

import android.content.Context
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "what time is it", "what's the date", "what day is today"
 *
 * Answered on device with no network, which matters: the most basic question
 * an assistant gets should not depend on an API key or a signal.
 */
class TimeSkill : Skill {

    override val id = "core.time"
    override val name = "Time & Date"
    override val priority = 78

    override fun canHandle(command: String): Boolean {
        val asking = CommandText.hasAnyPhrase(command, listOf("what", "tell", "whats"))
        if (!asking) return false
        return CommandText.hasAnyPhrase(command, listOf("time", "date", "day", "today"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        val now = Date()

        val wantsDate = CommandText.hasAnyPhrase(command, listOf("date", "day", "today"))
        val wantsTime = CommandText.hasPhrase(command, "time")

        return when {
            wantsDate && !wantsTime ->
                SkillResult.Ok(format("EEEE, d MMMM yyyy", now))
            wantsTime && !wantsDate ->
                SkillResult.Ok("It is " + format("h:mm a", now))
            else ->
                SkillResult.Ok(format("h:mm a", now) + " on " + format("EEEE, d MMMM", now))
        }
    }

    private fun format(pattern: String, date: Date): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}
