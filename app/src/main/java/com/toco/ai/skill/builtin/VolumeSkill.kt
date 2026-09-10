package com.toco.ai.skill.builtin

import android.content.Context
import android.media.AudioManager
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult

/**
 * "volume up", "increase volume", "mute", "volume 50"
 *
 * Uses AudioManager directly — a real API, no permission needed for the media
 * stream. Note that muting the RINGER stream would need DND policy access, so
 * this deliberately only touches media volume.
 */
class VolumeSkill : Skill {

    override val id = "core.volume"
    override val name = "Volume"

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase()
        if (c.contains("volume") || c.contains("sound level")) return true
        return c == "mute" || c == "unmute" || c.startsWith("mute ") ||
            c.startsWith("louder") || c.startsWith("quieter")
    }

    override fun execute(context: Context, command: String): SkillResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SkillResult.Failed("Audio service unavailable.")

        val c = command.lowercase()
        val stream = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream)

        // An explicit percentage wins over up/down.
        val percent = Regex("""(\d{1,3})\s*%?""").find(c)?.groupValues?.get(1)?.toIntOrNull()
        if (percent != null && (c.contains("set") || c.contains("%") || c.contains("to"))) {
            val level = (percent.coerceIn(0, 100) * max) / 100
            audio.setStreamVolume(stream, level, AudioManager.FLAG_SHOW_UI)
            return SkillResult.Ok("Volume set to $percent%")
        }

        return when {
            c.contains("unmute") -> {
                audio.setStreamVolume(stream, max / 2, AudioManager.FLAG_SHOW_UI)
                SkillResult.Ok("Unmuted")
            }
            c.contains("mute") || c.contains("silent") -> {
                audio.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                SkillResult.Ok("Muted")
            }
            c.contains("max") || c.contains("full") -> {
                audio.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                SkillResult.Ok("Volume at maximum")
            }
            c.contains("down") || c.contains("decrease") || c.contains("lower") ||
                c.contains("quieter") || c.contains("reduce") -> {
                audio.adjustStreamVolume(
                    stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                )
                SkillResult.Ok("Volume down")
            }
            else -> {
                audio.adjustStreamVolume(
                    stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                )
                SkillResult.Ok("Volume up")
            }
        }
    }
}
