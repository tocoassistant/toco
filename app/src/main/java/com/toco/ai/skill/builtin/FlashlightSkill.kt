package com.toco.ai.skill.builtin

import android.content.Context
import android.hardware.camera2.CameraManager
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult

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

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase()
        return c.contains("flashlight") || c.contains("torch") ||
            (c.contains("light") && (c.contains("on") || c.contains("off")))
    }

    override fun execute(context: Context, command: String): SkillResult {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return SkillResult.Failed("Camera service unavailable.")

        val c = command.lowercase()
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
