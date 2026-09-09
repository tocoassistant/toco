package com.toco.ai.skill.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.core.Prefs
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.AppFinder
import com.toco.ai.util.PhoneNumbers

/**
 * "whatsapp 03001234567" / "message 0300... on whatsapp"
 *
 * Opens the chat via the official wa.me deep link. Actually *sending* the text
 * without a tap needs an AccessibilityService — that arrives in a later phase.
 */
class WhatsAppSkill : Skill {

    override val id = "core.whatsapp"
    override val name = "WhatsApp Message"

    private val packageName = "com.whatsapp"

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase()
        return c.contains("whatsapp") && PhoneNumbers.extract(command) != null
    }

    override fun execute(context: Context, command: String): SkillResult {
        if (!AppFinder.isInstalled(context, packageName)) {
            return SkillResult.Failed("WhatsApp isn't installed on this phone.")
        }

        val raw = PhoneNumbers.extract(command)
            ?: return SkillResult.Failed("I couldn't find a number in that.")
        val number = PhoneNumbers.toWhatsApp(raw, Prefs(context).countryCode)
        val text = extractMessage(command)

        val uri = buildString {
            append("https://wa.me/").append(number)
            if (text.isNotBlank()) append("?text=").append(Uri.encode(text))
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SkillResult.Ok("Opening WhatsApp chat with $number")
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't open WhatsApp: ${e.message}")
        }
    }

    /** Grabs the part after "saying" / "that" / ":" as the message body. */
    private fun extractMessage(command: String): String {
        val markers = listOf(" saying ", " that ", ": ")
        for (m in markers) {
            val idx = command.lowercase().indexOf(m)
            if (idx >= 0) return command.substring(idx + m.length).trim()
        }
        return ""
    }
}
