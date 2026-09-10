package com.toco.ai.skill.builtin

import android.content.Context
import android.hardware.camera2.CameraManager
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText

/**
 * "flashlight on", "torch off", "turn on the light"
 *
 * CameraManager.setTorchMode needs no permission at all on API 23+. State is
 * tracked in the object because Android gives no direct "is torch on" query.
 */
class FlashlightSkill : Skill {

    override val id = "core.flashlight"
    override val name = "Flashlight"

    private var on = false

    override val priority = 70

    override fun canHandle(command: String): Boolean {
        // "torch" is normalised to "flashlight" before we get here.
        if (CommandText.hasPhrase(command, "flashlight")) return true

        // Plain "light" only counts with an on/off word, and only if the
        // command isn't about opening something.
        val words = CommandText.words(command)
        if (words.firstOrNull() in listOf("open", "launch", "search", "find")) return false

        return CommandText.hasPhrase(command, "light") &&
            CommandText.hasAnyPhrase(command, listOf("on", "off"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return SkillResult.Failed("Camera service unavailable.")

        val c = CommandText.normalize(command)
        val wantOn = when {
            c.contains("off") || c.contains("close") || c.contains("stop") -> false
            c.contains("on") || c.contains("open") || c.contains("start") -> true
            else -> !on
        }

        return try {
            val id = manager.cameraIdList.firstOrNull()
                ?: return SkillResult.Failed("No camera on this device.")
            manager.setTorchMode(id, wantOn)
            on = wantOn
            SkillResult.Ok(if (wantOn) "Flashlight on" else "Flashlight off")
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't control the flashlight: ${e.message}")
        }
    }
}
