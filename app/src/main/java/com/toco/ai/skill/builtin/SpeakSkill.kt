package com.toco.ai.skill.builtin

import android.content.Context
import com.toco.ai.core.Voice
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult

/** "say hello" / "speak good morning" — on-device TTS. */
class SpeakSkill : Skill {

    override val id = "core.speak"
    override val name = "Speak"

    private val triggers = listOf("say ", "speak ", "read ")

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase().trim()
        return triggers.any { c.startsWith(it) }
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = command.lowercase().trim()
        val trigger = triggers.first { c.startsWith(it) }
        val text = command.trim().substring(trigger.length).trim()

        if (text.isEmpty()) return SkillResult.Failed("Say what?")

        Voice.speak(context, text)
        return SkillResult.Ok("\"$text\"")
    }
}
