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
 * "call 03001234567", "call mom", "dial ammi", "phone my brother"
 *
 * Two-tier dialing, which is why this now works where it used to fail:
 *
 *   ACTION_CALL  places the call directly, but needs CALL_PHONE and is blocked
 *                outright by some ROMs.
 *   ACTION_DIAL  opens the dialer with the number filled in, needs NO
 *                permission, and cannot be blocked.
 *
 * So TOCO tries the direct call and quietly falls back to the dialer instead of
 * dead-ending. It does not declare CALL_PHONE as required, because the skill is
 * useful without it — declaring it meant the engine refused to run at all.
 */
class CallSkill : Skill {

    override val id = "core.call"
    override val name = "Phone Call"

    private val triggers = listOf("call", "dial", "phone", "ring")

    // Highest, because "call mom" must never be read as anything else.
    override val priority = 95

    override fun canHandle(command: String): Boolean {
        if (CommandText.hasAnyPhrase(command, listOf("whatsapp", "imo", "message"))) {
            return false
        }
        return CommandText.startsWithVerb(command, triggers)
    }

    override fun execute(context: Context, command: String): SkillResult {
        val literal = PhoneNumbers.extract(command)
        if (literal != null) {
            return dial(context, PhoneNumbers.toDialable(literal), literal)
        }

        val name = Contacts.nameFrom(command, triggers)
        if (name.isEmpty()) return SkillResult.Failed("Who should I call?")

        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.READ_CONTACTS,
                "To call \"$name\" I need your contacts."
            )
        }

        val matches = Contacts.findAll(context, name)

        if (matches.isEmpty()) {
            return SkillResult.Failed("No contact matching \"$name\".")
        }

        // One saved name, several numbers: ask rather than guess wrong.
        if (matches.size > 1) {
            return SkillResult.Choose(
                prompt = "Which $name?",
                options = matches.map { match ->
                    SkillResult.Choose.Option(
                        label = match.name,
                        detail = match.number,
                        // Dialling by number skips the lookup second time round.
                        command = "call " + match.number
                    )
                }
            )
        }

        val match = matches.first()
        return dial(context, PhoneNumbers.toDialable(match.number), match.name)
    }

    private fun dial(context: Context, number: String, label: String): SkillResult {
        // Direct call, if we're allowed.
        if (Permissions.has(context, Manifest.permission.CALL_PHONE)) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return SkillResult.Ok("Calling $label")
            } catch (e: Exception) {
                // ROM blocked it — fall through to the dialer.
            }
        }

        return try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            SkillResult.Ok("Dialer ready for $label — press call")
        } catch (e: Exception) {
            SkillResult.Failed("No dialer app on this phone.")
        }
    }
}
