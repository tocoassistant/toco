package com.toco.ai.skill.builtin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.Permissions
import com.toco.ai.util.PhoneNumbers

/** "call 03001234567" / "dial 0300..." */
class CallSkill : Skill {

    override val id = "core.call"
    override val name = "Phone Call"
    override val requiredPermissions = listOf(Manifest.permission.CALL_PHONE)

    private val triggers = listOf("call", "dial", "phone")

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase()
        if (c.contains("whatsapp") || c.contains("imo")) return false
        return triggers.any { c.startsWith(it) || c.contains(" $it ") } &&
                PhoneNumbers.extract(command) != null
    }

    override fun execute(context: Context, command: String): SkillResult {
        val raw = PhoneNumbers.extract(command)
            ?: return SkillResult.Failed("I couldn't find a number in that.")

        val number = PhoneNumbers.toDialable(raw)

        if (!Permissions.has(context, Manifest.permission.CALL_PHONE)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.CALL_PHONE,
                "TOCO needs call permission to dial for you."
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SkillResult.Ok("Calling $number")
        } catch (e: Exception) {
            SkillResult.Failed("Couldn't place the call: ${e.message}")
        }
    }
}
