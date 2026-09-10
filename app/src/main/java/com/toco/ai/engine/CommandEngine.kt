package com.toco.ai.engine

import android.content.Context
import com.toco.ai.ai.Ai
import com.toco.ai.ai.AIResult
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillRegistry
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.Permissions

/**
 * Single entry point for every command TOCO receives, typed or spoken.
 *
 *   command -> SkillRegistry.resolve()
 *      found     -> check permissions -> execute      (device action)
 *      not found -> Ai.ask()                          (conversation)
 *
 * The fallback is the important change. An unmatched command used to produce
 * "I don't have a module for that yet", which is a dead end for anything that
 * was never a device command to begin with — a question, a request for help.
 * Now whatever no module claims becomes a conversation with the model.
 *
 * [handle] does device actions only and is safe on the main thread.
 * [handleWithAi] may block on the network, so it must not run on it.
 */
class CommandEngine(
    private val moduleProvider: ModuleProvider = ModuleProvider.Unavailable
) {

    /** Outcome of a command that may have been answered by the AI. */
    sealed class Reply {
        /** A device action ran. */
        data class Action(val result: SkillResult) : Reply()

        /** The model answered conversationally. */
        data class Answer(val text: String) : Reply()

        /** Nothing could handle it, with the reason. */
        data class Unavailable(val message: String) : Reply()
    }

    /** True when this maps to a device action, so no network round trip is needed. */
    fun isDeviceCommand(rawCommand: String): Boolean =
        SkillRegistry.resolve(rawCommand.trim()) != null

    /**
     * Device actions only. Returns [SkillResult.NotHandled] when no module
     * claims it; the caller then decides whether to ask the AI.
     */
    fun handle(context: Context, rawCommand: String): SkillResult {
        val command = rawCommand.trim()
        if (command.isEmpty()) return SkillResult.NotHandled

        val skill = SkillRegistry.resolve(command)
            ?: return when (val outcome = moduleProvider.request(command)) {
                is ModuleProvider.Outcome.Created -> {
                    SkillRegistry.register(outcome.skill)
                    run(context, outcome.skill, command)
                }
                is ModuleProvider.Outcome.Rejected -> SkillResult.NotHandled
            }

        return run(context, skill, command)
    }

    /**
     * Full pipeline: device action if one fits, otherwise the model.
     * BLOCKING — call from a background thread.
     */
    fun handleWithAi(context: Context, rawCommand: String): Reply {
        val command = rawCommand.trim()
        if (command.isEmpty()) return Reply.Unavailable("Nothing to do.")

        val skill = SkillRegistry.resolve(command)
        if (skill != null) {
            return Reply.Action(run(context, skill, command))
        }

        if (!Ai.isReady()) {
            return Reply.Unavailable(
                "I have no module for that, and no AI key is set up yet."
            )
        }

        return when (val result = Ai.ask(command)) {
            is AIResult.Ok -> Reply.Answer(result.text)
            is AIResult.NotConfigured -> Reply.Unavailable(result.message)
            is AIResult.Failed -> Reply.Unavailable(result.message)
        }
    }

    private fun run(context: Context, skill: Skill, command: String): SkillResult {
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
