package com.toco.ai.skill.builtin

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult

/**
 * "play", "pause", "next song", "previous track"
 *
 * Sends real media key events through AudioManager, which whichever player
 * holds the media session receives. No permission required, and it works with
 * any player rather than being hardcoded to one app.
 */
class MediaSkill : Skill {

    override val id = "core.media"
    override val name = "Media Control"

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase().trim()
        val words = listOf(
            "play", "pause", "resume", "next song", "next track", "skip",
            "previous song", "previous track", "stop music", "play music"
        )
        return words.any { c == it || c.startsWith("$it ") || c.contains(" $it") }
    }

    override fun execute(context: Context, command: String): SkillResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SkillResult.Failed("Audio service unavailable.")

        val c = command.lowercase()
        val (code, label) = when {
            c.contains("next") || c.contains("skip") ->
                KeyEvent.KEYCODE_MEDIA_NEXT to "Next track"
            c.contains("previous") || c.contains("back track") ->
                KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Previous track"
            c.contains("pause") ->
                KeyEvent.KEYCODE_MEDIA_PAUSE to "Paused"
            c.contains("stop") ->
                KeyEvent.KEYCODE_MEDIA_STOP to "Stopped"
            c.contains("play") || c.contains("resume") ->
                KeyEvent.KEYCODE_MEDIA_PLAY to "Playing"
            else ->
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Toggled playback"
        }

        return try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            SkillResult.Ok(label)
        } catch (e: Exception) {
            SkillResult.Failed("No media player responded.")
        }
    }
}
