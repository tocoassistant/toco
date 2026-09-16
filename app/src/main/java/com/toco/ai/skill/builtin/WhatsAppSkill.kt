package com.toco.ai.skill.builtin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.core.Prefs
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.AppFinder
import com.toco.ai.util.CommandText
import com.toco.ai.util.Contacts
import com.toco.ai.util.Permissions
import com.toco.ai.util.PhoneNumbers

/**
 * "whatsapp mom saying hi", "whatsapp 0300... hello", "message ammi on whatsapp"
 *
 * Opens the chat with the text pre-filled via the official wa.me link. The
 * final send still needs one tap — pressing send for the user requires an
 * AccessibilityService, which is a later phase, and TOCO does not pretend
 * otherwise.
 */
class WhatsAppSkill : Skill {

    override val id = "core.whatsapp"
    override val name = "WhatsApp Message"

    private val packageName = "com.whatsapp"
    private val triggers = listOf("whatsapp", "wa", "message", "msg", "text", "send")

    override val priority = 90

    override fun canHandle(command: String): Boolean {
        // The app has to be named. "text mom" and "message mom" used to land
        // here, which meant a plain text message silently became a WhatsApp
        // one — surprising, and wrong if the contact does not use WhatsApp.
        // Those now go to SMS, which every phone can deliver.
        if (CommandText.hasPhrase(command, "imo")) return false
        return CommandText.hasAnyPhrase(command, listOf("whatsapp", "wa"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        if (!AppFinder.isInstalled(context, packageName)) {
            return SkillResult.Failed("WhatsApp isn't installed on this phone.")
        }

        val text = extractMessage(command)
        val literal = PhoneNumbers.extract(command)

        if (literal != null) {
            val number = PhoneNumbers.toWhatsApp(literal, Prefs(context).countryCode)
            return open(context, number, text, number)
        }

        val name = Contacts.nameFrom(stripAppWords(command), triggers)
        if (name.isEmpty()) {
            return SkillResult.Failed("Who should I message?")
        }

        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.READ_CONTACTS,
                "To message \"$name\" I need access to your contacts."
            )
        }

        val matches = Contacts.findAll(context, name)

        if (matches.isEmpty()) {
            return SkillResult.Failed("No contact matching \"$name\".")
        }

        if (matches.size > 1) {
            val suffix = if (text.isBlank()) "" else " saying $text"
            return SkillResult.Choose(
                prompt = "Which $name?",
                options = matches.map { match ->
                    SkillResult.Choose.Option(
                        label = match.name,
                        detail = match.number,
                        command = "whatsapp " + match.number + suffix
                    )
                }
            )
        }

        val match = matches.first()
        val number = PhoneNumbers.toWhatsApp(match.number, Prefs(context).countryCode)
        return open(context, number, text, match.name)
    }

    private fun open(
        context: Context,
        number: String,
        text: String,
        label: String
    ): SkillResult {
        val uri = buildString {
            append("https://wa.me/").append(number)
            if (text.isNotBlank()) append("?text=").append(Uri.encode(text))
        }

        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            if (text.isNotBlank()) {
                SkillResult.Ok("Chat with $label ready — tap send")
            } else {
                SkillResult.Ok("Opening WhatsApp chat with $label")
            }
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't open WhatsApp: ${e.message}")
        }
    }

    /** Removes app-name words so the remainder can be read as a contact name. */
    private fun stripAppWords(command: String): String =
        command.lowercase()
            .replace("on whatsapp", " ")
            .replace("via whatsapp", " ")
            .replace("whatsapp", " ")
            .trim()

    /** Text after "saying" / "that" / "-" is the message body. */
    private fun extractMessage(command: String): String {
        val markers = listOf(" saying ", " and say ", " that says ", " that ", " - ", ": ")
        for (marker in markers) {
            val at = command.lowercase().indexOf(marker)
            if (at >= 0) return command.substring(at + marker.length).trim()
        }
        return ""
    }
}
