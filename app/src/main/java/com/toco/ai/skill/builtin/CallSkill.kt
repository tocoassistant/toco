package com.toco.ai.skill.builtin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.Contacts
import com.toco.ai.util.Permissions
import com.toco.ai.util.PhoneNumbers

/**
 * "call 03001234567", "call mom", "dial ammi", "phone my brother"
 *
 * Claims the command on the trigger word ALONE. It used to also require a
 * number to be present, which meant "call mom" fell through and TOCO reported
 * "no module for that yet" — technically true, but useless. Claiming early
 * lets execute() explain the real problem instead.
 */
class CallSkill : Skill {

    override val id = "core.call"
    override val name = "Phone Call"
    override val requiredPermissions = listOf(Manifest.permission.CALL_PHONE)

    private val triggers = listOf("call", "dial", "phone", "ring")

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase().trim()
        if (c.contains("whatsapp") || c.contains("imo")) return false
        return triggers.any { c == it || c.startsWith("$it ") || c.contains(" $it ") }
    }

    override fun execute(context: Context, command: String): SkillResult {
        if (!Permissions.has(context, Manifest.permission.CALL_PHONE)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.CALL_PHONE,
                "TOCO needs call permission to dial for you."
            )
        }

        // A literal number in the text always wins over a contact lookup.
        val literal = PhoneNumbers.extract(command)
        if (literal != null) {
            return dial(context, PhoneNumbers.toDialable(literal), literal)
        }

        val name = Contacts.nameFrom(command, triggers)
        if (name.isEmpty()) {
            return SkillResult.Failed("Who should I call?")
        }

        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.READ_CONTACTS,
                "To call \"$name\" I need access to your contacts."
            )
        }

        val match = Contacts.findByName(context, name)
            ?: return SkillResult.Failed("No contact matching \"$name\".")

        return dial(context, PhoneNumbers.toDialable(match.number), match.name)
    }

    private fun dial(context: Context, number: String, label: String): SkillResult =
        try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            SkillResult.Ok("Calling $label")
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't place the call: ${e.message}")
        }
}
