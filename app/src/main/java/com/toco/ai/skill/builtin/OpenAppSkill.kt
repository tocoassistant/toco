package com.toco.ai.skill.builtin

import android.content.Context
import android.content.Intent
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.AppFinder

/** "open youtube" / "launch settings" / "start camera" */
class OpenAppSkill : Skill {

    override val id = "core.open_app"
    override val name = "Open App"

    private val triggers = listOf("open ", "launch ", "start ", "run ")

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase().trim()
        return triggers.any { c.startsWith(it) }
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = command.lowercase().trim()
        val trigger = triggers.first { c.startsWith(it) }
        val query = command.trim().substring(trigger.length).trim()

        if (query.isEmpty()) return SkillResult.Failed("Open what?")

        val app = AppFinder.find(context, query)
            ?: return SkillResult.Failed("I couldn't find an app called \"$query\".")

        val launch: Intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return SkillResult.Failed("${app.label} can't be opened directly.")

        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SkillResult.Ok("Opening ${app.label}")
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't open ${app.label}: ${e.message}")
        }
    }
}
