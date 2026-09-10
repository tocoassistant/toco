package com.toco.ai.skill.builtin

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult

/**
 * "open wifi settings", "turn on bluetooth", "open display settings"
 *
 * Important honesty note: Android removed the ability for normal apps to
 * toggle Wi-Fi (API 29) and made BluetoothAdapter.enable() a no-op (API 33).
 * So a request to "turn on wifi" opens the settings screen and TOCO says
 * exactly that, rather than claiming it flipped the switch.
 */
class SettingsSkill : Skill {

    override val id = "core.settings"
    override val name = "Android Settings"

    private data class Target(
        val keywords: List<String>,
        val action: String,
        val label: String,
        /** True when Android forbids apps from changing this directly. */
        val userMustToggle: Boolean = false
    )

    private val targets = listOf(
        Target(listOf("wifi", "wi-fi", "wireless"), Settings.ACTION_WIFI_SETTINGS, "Wi-Fi", true),
        Target(listOf("bluetooth"), Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth", true),
        Target(listOf("display", "brightness", "screen"), Settings.ACTION_DISPLAY_SETTINGS, "Display"),
        Target(listOf("sound", "audio", "ringtone"), Settings.ACTION_SOUND_SETTINGS, "Sound"),
        Target(listOf("battery"), "android.settings.BATTERY_SAVER_SETTINGS", "Battery"),
        Target(listOf("data", "mobile data", "network"), Settings.ACTION_WIRELESS_SETTINGS, "Network", true),
        Target(listOf("location", "gps"), Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location", true),
        Target(listOf("accessibility"), Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility"),
        Target(listOf("notification"), "android.settings.NOTIFICATION_SETTINGS", "Notifications"),
        Target(listOf("airplane", "flight"), Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode", true),
        Target(listOf("storage"), "android.settings.INTERNAL_STORAGE_SETTINGS", "Storage"),
        Target(listOf("date", "time"), Settings.ACTION_DATE_SETTINGS, "Date & time"),
        Target(listOf("language", "keyboard"), "android.settings.LOCALE_SETTINGS", "Language"),
        Target(listOf("developer"), "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", "Developer options")
    )

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase()
        val mentionsSettings = c.contains("setting") || c.contains("open") ||
            c.contains("turn on") || c.contains("turn off") || c.contains("enable") ||
            c.contains("disable") || c.contains("take me to")
        if (!mentionsSettings) return false
        return targets.any { t -> t.keywords.any { c.contains(it) } } ||
            c.trim() == "settings" || c.contains("android settings")
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = command.lowercase()
        val target = targets.firstOrNull { t -> t.keywords.any { c.contains(it) } }

        val action = target?.action ?: Settings.ACTION_SETTINGS
        val label = target?.label ?: "Settings"

        return try {
            context.startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

            val askedToToggle = c.contains("turn on") || c.contains("turn off") ||
                c.contains("enable") || c.contains("disable")

            if (target?.userMustToggle == true && askedToToggle) {
                SkillResult.Ok(
                    "Android doesn't let apps switch $label. I opened the settings — flip it there."
                )
            } else {
                SkillResult.Ok("Opened $label settings")
            }
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't open $label settings on this phone.")
        }
    }
}
