package com.toco.ai.skill.builtin

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText

/**
 * Phone-wide sound profiles rather than just media-volume shortcuts.
 *
 * Understands natural requests such as:
 *  - "set my phone to silent", "zero sound", "no sound", "meeting mode"
 *  - "vibrate only", "put the phone on vibrate"
 *  - "ring mode", "sound back on", "normal sound"
 *  - "do not disturb", "dnd on/off"
 *
 * Android protects changes that cross into DND/silent policy. When policy access
 * is missing TOCO opens the exact system permission page instead of pretending
 * the command succeeded.
 */
class SoundModeSkill : Skill {
    override val id = "core.sound_mode"
    override val name = "Sound Mode"
    override val priority = 95

    override fun canHandle(command: String): Boolean {
        val c = CommandText.normalize(command)
        if (c.contains("volume") && !c.contains("silent mode")) return false
        return phrases.any { c.contains(it) }
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = CommandText.normalize(command)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SkillResult.Failed("Audio service unavailable.")
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        return when {
            isDndOff(c) -> setDnd(context, notifications, false)
            isDndOn(c) -> setDnd(context, notifications, true)
            isVibrate(c) -> setRinger(context, audio, AudioManager.RINGER_MODE_VIBRATE, "Vibrate mode on")
            isNormal(c) -> setRinger(context, audio, AudioManager.RINGER_MODE_NORMAL, "Ring mode on")
            else -> silentEverything(context, audio)
        }
    }

    private fun silentEverything(context: Context, audio: AudioManager): SkillResult {
        if (!hasPolicyAccess(context)) return requestPolicyAccess(context)
        return try {
            // A real "silent phone" command: silence the ringer/notifications and
            // media, rather than only muting whatever song happens to be playing.
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
            SkillResult.Ok("Phone is silent")
        } catch (e: SecurityException) {
            requestPolicyAccess(context)
        }
    }

    private fun setRinger(context: Context, audio: AudioManager, mode: Int, message: String): SkillResult {
        if (!hasPolicyAccess(context)) return requestPolicyAccess(context)
        return try {
            audio.ringerMode = mode
            if (mode == AudioManager.RINGER_MODE_NORMAL && audio.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                audio.setStreamVolume(AudioManager.STREAM_RING, (max / 2).coerceAtLeast(1), 0)
            }
            SkillResult.Ok(message)
        } catch (e: SecurityException) {
            requestPolicyAccess(context)
        }
    }

    private fun setDnd(context: Context, nm: NotificationManager?, enabled: Boolean): SkillResult {
        if (nm == null) return SkillResult.Failed("Notification service unavailable.")
        if (!hasPolicyAccess(context)) return requestPolicyAccess(context)
        return try {
            nm.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            SkillResult.Ok(if (enabled) "Do Not Disturb on" else "Do Not Disturb off")
        } catch (e: SecurityException) {
            requestPolicyAccess(context)
        }
    }

    private fun hasPolicyAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    private fun requestPolicyAccess(context: Context): SkillResult {
        return try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            SkillResult.Failed("Allow TOCO Do Not Disturb access once, then repeat the command.")
        } catch (_: Exception) {
            SkillResult.Failed("TOCO needs Do Not Disturb access to change the phone sound mode.")
        }
    }

    private fun isDndOn(c: String) =
        (c.contains("do not disturb") || c.contains("dnd")) && !isDndOff(c)

    private fun isDndOff(c: String) =
        (c.contains("do not disturb") || c.contains("dnd")) &&
            listOf("off", "disable", "stop", "normal").any { c.contains(it) }

    private fun isVibrate(c: String) = c.contains("vibrate") || c.contains("vibration only")

    private fun isNormal(c: String) = listOf(
        "ring mode", "ringer on", "sound back on", "normal sound", "normal mode",
        "silent off", "turn sound on", "unsilence"
    ).any { c.contains(it) }

    companion object {
        private val phrases = listOf(
            "silent mode", "phone silent", "set phone to silent", "set my phone to silent",
            "zero sound", "no sound", "all sound off", "sound off", "meeting mode",
            "vibrate", "vibration only", "ring mode", "ringer on", "sound back on",
            "normal sound", "normal mode", "silent off", "turn sound on", "unsilence",
            "do not disturb", "dnd"
        )
    }
}
