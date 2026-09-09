package com.toco.ai.skill

/**
 * The only thing a Skill is allowed to hand back.
 * Keeping this sealed means the UI can never be surprised by a new outcome type.
 */
sealed class SkillResult {
    data class Ok(val message: String) : SkillResult()
    data class Failed(val message: String) : SkillResult()
    data class NeedsPermission(val permission: String, val reason: String) : SkillResult()
    object NotHandled : SkillResult()
}
