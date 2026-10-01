package com.toco.ai.skill.builtin

import android.content.Context
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText

/**
 * Local help: never wastes an AI request just to explain commands that TOCO
 * already knows how to run on-device.
 */
class HelpSkill : Skill {
    override val id = "core.help"
    override val name = "TOCO Help"
    override val priority = 100

    override fun canHandle(command: String): Boolean {
        val c = CommandText.normalize(command)
        return c in setOf("help", "commands", "show commands", "show command", "what can you do") ||
            CommandText.hasAnyPhrase(command, listOf(
                "what can toco do", "working commands", "available commands", "command list"
            ))
    }

    override fun execute(context: Context, command: String): SkillResult = SkillResult.Ok(
        "Try these: Set my phone to silent · Vibrate only · Ring mode · DND on/off · " +
            "Meeting mode · Zero sound · Open YouTube · Flashlight on/off · Volume 50% · " +
            "Play/Pause/Next song · Battery · Storage · Set timer for 5 minutes · " +
            "Set alarm for 7 AM · Navigate to Dhaka · Search web for weather. " +
            "You can also chain actions, e.g. ‘open YouTube then volume 40%’."
    )
}
