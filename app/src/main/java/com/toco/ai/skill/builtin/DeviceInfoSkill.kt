package com.toco.ai.skill.builtin

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText

/**
 * "battery", "how much battery", "device info", "android version", "storage"
 *
 * Reads only what is already available without permissions. Nothing here is
 * collected or sent anywhere.
 */
class DeviceInfoSkill : Skill {

    override val id = "core.device_info"
    override val name = "Device Info"

    override val priority = 65

    override fun canHandle(command: String): Boolean {
        val words = CommandText.words(command)
        if (words.firstOrNull() in listOf("open", "launch", "search", "find")) return false

        return CommandText.hasAnyPhrase(
            command,
            listOf("battery", "charging", "storage", "space", "android version")
        ) || CommandText.hasAnyPhrase(command, listOf("device info", "phone info"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = CommandText.normalize(command)

        return when {
            c.contains("battery") || c.contains("charge") -> battery(context)
            c.contains("storage") || c.contains("space") -> storage()
            else -> SkillResult.Ok(
                "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
                    "(API ${Build.VERSION.SDK_INT})"
            )
        }
    }

    private fun battery(context: Context): SkillResult {
        val status = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return SkillResult.Failed("Couldn't read battery state.")

        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return SkillResult.Failed("Couldn't read battery level.")

        val percent = level * 100 / scale
        val plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

        return SkillResult.Ok(
            if (plugged) "Battery $percent%, charging" else "Battery $percent%"
        )
    }

    private fun storage(): SkillResult {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024 * 1024)
            val totalGb = (stat.blockCountLong * stat.blockSizeLong) / (1024.0 * 1024 * 1024)
            SkillResult.Ok(
                String.format("%.1f GB free of %.1f GB", freeGb, totalGb)
            )
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't read storage info.")
        }
    }
}
