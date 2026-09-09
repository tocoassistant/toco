package com.toco.ai.skill

import android.content.Context

/**
 * A single ability TOCO has.
 *
 * Every skill — built-in today, AI-generated later — implements exactly this.
 * That is the whole point: the engine never needs to know where a skill came from.
 */
interface Skill {

    /** Stable unique id, e.g. "core.call". Generated skills get "user.<uuid>". */
    val id: String

    /** Human label, shown in the Models screen. */
    val name: String

    /** Permissions this skill needs before it can run. */
    val requiredPermissions: List<String>
        get() = emptyList()

    /** Cheap, no side effects. Return true if this skill claims the command. */
    fun canHandle(command: String): Boolean

    /** Do the actual work. Only called after canHandle() returned true. */
    fun execute(context: Context, command: String): SkillResult
}
