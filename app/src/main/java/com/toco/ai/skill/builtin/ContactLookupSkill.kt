package com.toco.ai.skill.builtin

import android.Manifest
import android.content.Context
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText
import com.toco.ai.util.Contacts
import com.toco.ai.util.Permissions

/**
 * "find Rohim in contacts", "show Ma's number", "what is Bablu's number"
 *
 * Answers without dialling. Looking a number up and calling it are different
 * intentions, and conflating them means an accidental call to the wrong person.
 */
class ContactLookupSkill : Skill {

    override val id = "core.contact_lookup"
    override val name = "Contact Lookup"
    override val priority = 94

    override fun canHandle(command: String): Boolean {
        val c = CommandText.normalize(command)

        if (CommandText.hasPhrase(command, "contacts") &&
            CommandText.startsWithVerb(command, listOf("find", "search", "show", "look"))
        ) {
            return true
        }

        // "show X's number", "what is X's number"
        return c.contains("number") &&
            CommandText.hasAnyPhrase(command, listOf("show", "what", "whats", "find", "give"))
    }

    override fun execute(context: Context, command: String): SkillResult {
        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.READ_CONTACTS,
                "I need contacts access to look that up."
            )
        }

        val name = extractName(command)
        if (name.isEmpty()) return SkillResult.Failed("Whose number do you want?")

        val matches = Contacts.findAll(context, name)
        if (matches.isEmpty()) return SkillResult.Failed("No contact matching \"$name\".")

        if (matches.size == 1) {
            val match = matches.first()
            return SkillResult.Ok(match.name + ": " + match.number)
        }

        return SkillResult.Ok(
            matches.size.toString() + " matches \u00b7 " +
                matches.joinToString("  \u00b7  ") { it.name + " " + it.number }
        )
    }

    private fun extractName(command: String): String {
        var text = CommandText.normalize(command)

        for (word in listOf(
            "find", "search", "show", "look up", "look for", "give me",
            "what is", "whats", "in contacts", "contact", "contacts",
            "number", "phone", "the", "me", "for", "of", "'s", "s number"
        )) {
            text = text.replace(word, " ")
        }

        return text.replace(Regex("\\s+"), " ").trim().trim(',', '.', '?')
    }
}
