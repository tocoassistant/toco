package com.toco.ai.skill.builtin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText
import com.toco.ai.util.Contacts
import com.toco.ai.util.Permissions
import com.toco.ai.util.PhoneNumbers

/**
 * "sms mom saying I'm late", "text 03001234567 hello"
 *
 * Opens the messaging app with the recipient and text already filled in. The
 * send button is still the user's to press: TOCO holds no SEND_SMS permission,
 * because an assistant that can silently send messages on your behalf is a
 * much bigger thing to trust than one that drafts them.
 */
class SmsSkill : Skill {

    override val id = "core.sms"
    override val name = "Text Message"
    override val priority = 88

    private val triggers = listOf("sms", "text", "message", "msg")

    override fun canHandle(command: String): Boolean {
        // WhatsApp and imo name themselves; anything else that means "send
        // words to a person" is a normal text.
        if (CommandText.hasAnyPhrase(command, listOf("whatsapp", "wa", "imo"))) return false
        return CommandText.startsWithVerb(command, triggers) ||
            CommandText.hasPhrase(command, "sms")
    }

    override fun execute(context: Context, command: String): SkillResult {
        val body = messageBody(command)
        val literal = PhoneNumbers.extract(command)

        if (literal != null) {
            return compose(context, PhoneNumbers.toDialable(literal), body, literal)
        }

        val name = Contacts.nameFrom(stripAppWords(command), triggers)
        if (name.isEmpty()) return SkillResult.Failed("Who should I text?")

        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.READ_CONTACTS,
                "To text \"$name\" I need your contacts."
            )
        }

        val matches = Contacts.findAll(context, name)
        if (matches.isEmpty()) return SkillResult.Failed("No contact matching \"$name\".")

        if (matches.size > 1) {
            val suffix = if (body.isBlank()) "" else " saying $body"
            return SkillResult.Choose(
                prompt = "Which $name?",
                options = matches.map {
                    SkillResult.Choose.Option(it.name, it.number, "sms " + it.number + suffix)
                }
            )
        }

        val match = matches.first()
        return compose(context, PhoneNumbers.toDialable(match.number), body, match.name)
    }

    private fun compose(
        context: Context,
        number: String,
        body: String,
        label: String
    ): SkillResult = try {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (body.isNotBlank()) intent.putExtra("sms_body", body)

        context.startActivity(intent)

        if (body.isBlank()) SkillResult.Ok("Message to $label ready")
        else SkillResult.Ok("Message to $label ready — tap send")
    } catch (e: Exception) {
        SkillResult.Failed("No messaging app on this phone.")
    }

    private fun stripAppWords(command: String): String =
        command.lowercase()
            .replace("sms", " ")
            .replace("text", " ")
            .replace("message", " ")
            .replace("msg", " ")
            .trim()

    private fun messageBody(command: String): String {
        for (marker in listOf(" saying ", " that says ", " and say ", " - ", ": ")) {
            val at = command.lowercase().indexOf(marker)
            if (at >= 0) return command.substring(at + marker.length).trim()
        }
        return ""
    }
}
