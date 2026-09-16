package com.toco.ai.skill.builtin

import android.content.Context
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText

/**
 * "what is 15% of 200", "calculate 45 times 12", "250 divided by 4"
 *
 * Handled on device rather than sent to the model: arithmetic is instant and
 * exact here, whereas a round trip is slow, needs a key, costs money, and a
 * language model can be confidently wrong about numbers.
 */
class MathSkill : Skill {

    override val id = "core.math"
    override val name = "Calculator"
    override val priority = 79

    override fun canHandle(command: String): Boolean {
        val c = CommandText.normalize(command)
        if (!c.any { it.isDigit() }) return false

        return CommandText.hasAnyPhrase(command, listOf("calculate", "what is", "whats", "how much")) ||
            Regex("""\d+\s*(%|percent|plus|minus|times|divided|x|\+|-|\*|/)\s*""").containsMatchIn(c)
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = CommandText.normalize(command)
            .replace("calculate", " ")
            .replace("what is", " ")
            .replace("whats", " ")
            .replace("how much is", " ")
            .trim()

        percentOf(c)?.let { return SkillResult.Ok(it) }
        binary(c)?.let { return SkillResult.Ok(it) }

        return SkillResult.Failed("I couldn't work that one out.")
    }

    /** "15% of 200" and "15 percent of 200". */
    private fun percentOf(c: String): String? {
        val m = Regex("""([\d.]+)\s*(?:%|percent)\s*of\s*([\d.]+)""").find(c) ?: return null
        val percent = m.groupValues[1].toDoubleOrNull() ?: return null
        val whole = m.groupValues[2].toDoubleOrNull() ?: return null
        return trim(percent * whole / 100)
    }

    private fun binary(c: String): String? {
        val m = Regex(
            """([\d.]+)\s*(plus|minus|times|multiplied by|divided by|over|[+\-*/x])\s*([\d.]+)"""
        ).find(c) ?: return null

        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val b = m.groupValues[3].toDoubleOrNull() ?: return null

        val result = when (m.groupValues[2]) {
            "plus", "+" -> a + b
            "minus", "-" -> a - b
            "times", "multiplied by", "*", "x" -> a * b
            "divided by", "over", "/" -> {
                if (b == 0.0) return "You can't divide by zero."
                a / b
            }
            else -> return null
        }

        return trim(result)
    }

    /** Whole numbers read back as whole numbers, not "12.0". */
    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.2f", value)
}
