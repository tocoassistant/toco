package com.toco.ai.skill.builtin

import android.content.Context
import android.media.AudioManager
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.core.Prefs
import com.toco.ai.util.CommandText

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

    override val priority = 60

    override fun canHandle(command: String): Boolean {
        val words = CommandText.words(command)
        val first = words.firstOrNull() ?: return false

        if (first in listOf("open", "launch", "search", "find")) return false

        if (CommandText.hasPhrase(command, "volume")) return true
        if (first in listOf("mute", "unmute")) return true

        return false
    }

    override fun execute(context: Context, command: String): SkillResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SkillResult.Failed("Audio service unavailable.")

        val c = CommandText.normalize(command)

        // "toco volume up" adjusts TOCO's own voice, which lives on the call
        // stream when that setting is on. Everything else means media volume,
        // since that is what the user is usually listening to.
        val aboutToco = CommandText.hasAnyPhrase(command, listOf("toco", "your", "voice"))
        val stream = if (aboutToco && Prefs(context).loudVoice) {
            AudioManager.STREAM_RING
        } else {
            AudioManager.STREAM_MUSIC
        }
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
