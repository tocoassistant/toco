package com.toco.ai.skill.builtin

import android.content.Context
import android.media.AudioManager
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText

/**
 * "speaker on", "mute call", "switch to bluetooth", "switch to phone"
 *
 * Controls audio routing during a live call. Every one of these is a real
 * AudioManager operation; nothing here pretends to do something Android
 * forbids, and each refuses honestly when no call is in progress rather than
 * reporting success into the void.
 */
class CallControlSkill : Skill {

    override val id = "core.call_control"
    override val name = "Call Controls"
    override val priority = 92

    override fun canHandle(command: String): Boolean {
        val mentionsAudio = CommandText.hasAnyPhrase(
            command,
            listOf("speaker", "speakerphone", "loudspeaker", "earpiece", "bluetooth", "headset")
        ) || (
            // "switch audio to phone" names no device, only a destination.
            CommandText.hasAnyPhrase(command, listOf("switch", "route")) &&
                CommandText.hasAnyPhrase(command, listOf("audio", "sound", "phone"))
        )
        val mentionsMute = CommandText.hasAnyPhrase(command, listOf("mute", "unmute"))

        if (mentionsAudio) return true

        // Plain "mute" belongs to media volume; only claim it when the command
        // is clearly about the call.
        return mentionsMute && CommandText.hasAnyPhrase(command, listOf("call", "mic", "microphone"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SkillResult.Failed("Audio service unavailable.")

        if (!inCall(audio)) {
            return SkillResult.Failed("There is no call in progress.")
        }

        val c = CommandText.normalize(command)
        val off = CommandText.hasAnyPhrase(command, listOf("off", "disable", "stop"))

        return when {
            CommandText.hasAnyPhrase(command, listOf("bluetooth", "headset")) ->
                toBluetooth(audio)

            CommandText.hasAnyPhrase(command, listOf("earpiece")) ||
                (c.contains("phone") && c.contains("switch")) ->
                toEarpiece(audio)

            CommandText.hasAnyPhrase(command, listOf("speaker", "speakerphone", "loudspeaker")) ->
                if (off) toEarpiece(audio) else toSpeaker(audio)

            CommandText.hasPhrase(command, "unmute") -> setMic(audio, false)
            CommandText.hasPhrase(command, "mute") -> setMic(audio, true)

            else -> SkillResult.Failed("I didn't catch which call control you meant.")
        }
    }

    private fun inCall(audio: AudioManager): Boolean = try {
        audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
    } catch (e: Exception) {
        false
    }

    private fun toSpeaker(audio: AudioManager): SkillResult = try {
        audio.stopBluetoothSco()
        audio.isBluetoothScoOn = false
        audio.isSpeakerphoneOn = true
        SkillResult.Ok("Speaker on")
    } catch (e: Exception) {
        SkillResult.Failed("Couldn't switch to speaker.")
    }

    private fun toEarpiece(audio: AudioManager): SkillResult = try {
        audio.stopBluetoothSco()
        audio.isBluetoothScoOn = false
        audio.isSpeakerphoneOn = false
        SkillResult.Ok("Earpiece")
    } catch (e: Exception) {
        SkillResult.Failed("Couldn't switch audio.")
    }

    private fun toBluetooth(audio: AudioManager): SkillResult = try {
        audio.isSpeakerphoneOn = false
        audio.startBluetoothSco()
        audio.isBluetoothScoOn = true
        SkillResult.Ok("Bluetooth")
    } catch (e: Exception) {
        SkillResult.Failed("No Bluetooth audio device connected.")
    }

    private fun setMic(audio: AudioManager, mute: Boolean): SkillResult = try {
        audio.isMicrophoneMute = mute
        SkillResult.Ok(if (mute) "Muted" else "Unmuted")
    } catch (e: Exception) {
        SkillResult.Failed("Couldn't change the microphone.")
    }
}
