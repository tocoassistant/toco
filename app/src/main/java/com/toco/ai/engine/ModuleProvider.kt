package com.toco.ai.engine

import com.toco.ai.skill.Skill

/**
 * The seam where "create a new module on demand" will live.
 *
 * Phase 1 (now): [Unavailable] — TOCO honestly says it has no module yet.
 * Phase 2 (next): a real implementation that asks the backend to compose a new
 *                 skill out of existing primitives (intents + accessibility
 *                 actions), then hands it back to be registered.
 *
 * Deliberately an interface, so the app can be built and shipped today and the
 * backend can be swapped in without touching the engine, the UI, or any skill.
 */
interface ModuleProvider {

    sealed class Outcome {
        data class Created(val skill: Skill) : Outcome()
        data class Rejected(val reason: String) : Outcome()
    }

    fun request(command: String): Outcome

    /** Default: no generation. TOCO simply reports the gap. */
    object Unavailable : ModuleProvider {
        override fun request(command: String): Outcome =
            Outcome.Rejected("I don't have a module for that yet.")
    }
}
