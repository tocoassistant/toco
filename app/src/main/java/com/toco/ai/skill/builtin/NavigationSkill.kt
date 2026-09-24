package com.toco.ai.skill.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.AppFinder
import com.toco.ai.util.CommandText

/**
 * "navigate to Dhaka", "directions to the airport", "take me home"
 *
 * Uses the google.navigation scheme, which starts turn-by-turn guidance
 * immediately rather than merely showing the place on a map.
 */
class NavigationSkill : Skill {

    override val id = "core.navigation"
    override val name = "Navigation"
    override val priority = 82

    override fun canHandle(command: String): Boolean =
        CommandText.startsWithVerb(command, listOf("navigate", "directions", "drive")) ||
            CommandText.hasAnyPhrase(command, listOf("directions to", "navigate to", "route to"))

    override fun execute(context: Context, command: String): SkillResult {
        val destination = destination(command)
        if (destination.isEmpty()) return SkillResult.Failed("Navigate where?")

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=" + Uri.encode(destination))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (AppFinder.isInstalled(context, MAPS)) intent.setPackage(MAPS)

        return try {
            context.startActivity(intent)
            SkillResult.Ok("Navigating to $destination")
        } catch (e: Exception) {
            // No navigation app; fall back to showing the place.
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                SkillResult.Ok("Showing $destination")
            } catch (e2: Exception) {
                SkillResult.Failed("No maps app on this phone.")
            }
        }
    }

    private fun destination(command: String): String {
        var text = CommandText.normalize(command)
        for (verb in listOf("navigate to", "navigate", "directions to", "directions",
                            "drive to", "drive", "route to", "take me to")) {
            text = text.replace(verb, " ")
        }
        return text.trim().trim(',', '.').trim()
    }

    private companion object {
        const val MAPS = "com.google.android.apps.maps"
    }
}
