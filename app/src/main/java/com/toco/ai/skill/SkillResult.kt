package com.toco.ai.skill

/**
 * The only thing a Skill is allowed to hand back.
 * Keeping this sealed means the UI can never be surprised by a new outcome type.
 */
sealed class SkillResult {
    data class Ok(val message: String) : SkillResult()
    data class Failed(val message: String) : SkillResult()
    data class NeedsPermission(val permission: String, val reason: String) : SkillResult()
    /**
     * Several contacts matched, so TOCO refuses to guess and hands the choice
     * back. [label] is what to show, [value] what to run if picked.
     */
    data class Choose(
        val prompt: String,
        val options: List<Option>
    ) : SkillResult() {
        data class Option(val label: String, val detail: String, val command: String)
    }

    object NotHandled : SkillResult()
}
