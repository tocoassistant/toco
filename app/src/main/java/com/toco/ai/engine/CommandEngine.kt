package com.toco.ai.engine

import android.content.Context
import com.toco.ai.skill.SkillRegistry
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.Permissions

/**
 * The single entry point for every command TOCO receives, typed or spoken.
 *
 * Flow (matches the TOCO concept exactly):
 *
 *   command -> SkillRegistry.resolve()
 *      found     -> check permissions -> execute
 *      not found -> ModuleProvider.request()   [phase 2: backend generates a module]
 *
 * Nothing above this class knows or cares whether a skill was built in or
 * generated on demand. That separation is what makes the self-improving part
 * possible without rewriting the UI.
 */
class CommandEngine(
    private val moduleProvider: ModuleProvider = ModuleProvider.Unavailable
) {

    fun handle(context: Context, rawCommand: String): SkillResult {
        val command = rawCommand.trim()
        if (command.isEmpty()) return SkillResult.NotHandled

        val skill = SkillRegistry.resolve(command)

        if (skill == null) {
            // No module exists. This is the hook for backend module generation.
            return when (val outcome = moduleProvider.request(command)) {
                is ModuleProvider.Outcome.Created -> {
                    SkillRegistry.register(outcome.skill)
                    run(context, outcome.skill, command)
                }
                is ModuleProvider.Outcome.Rejected ->
                    SkillResult.Failed(outcome.reason)
            }
        }

        return run(context, skill, command)
    }

    private fun run(
        context: Context,
        skill: com.toco.ai.skill.Skill,
        command: String
    ): SkillResult {
        val missing = Permissions.missing(context, skill.requiredPermissions)
        if (missing.isNotEmpty()) {
            return SkillResult.NeedsPermission(
                missing.first(),
                "${skill.name} needs permission first."
            )
        }

        return try {
            skill.execute(context, command)
        } catch (e: Exception) {
            SkillResult.Failed("${skill.name} failed: ${e.message}")
        }
    }
}
