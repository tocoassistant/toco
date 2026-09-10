package com.toco.ai.skill

import com.toco.ai.skill.builtin.CallSkill
import com.toco.ai.skill.builtin.DeviceInfoSkill
import com.toco.ai.skill.builtin.FlashlightSkill
import com.toco.ai.skill.builtin.MediaSkill
import com.toco.ai.skill.builtin.OpenAppSkill
import com.toco.ai.skill.builtin.SettingsSkill
import com.toco.ai.skill.builtin.SpeakSkill
import com.toco.ai.skill.builtin.VolumeSkill
import com.toco.ai.skill.builtin.WhatsAppSkill

/**
 * Holds every skill TOCO currently knows.
 *
 * Built-ins are registered at startup. Later, when the backend generates a new
 * module for a user, it gets registered here too via [register] — the engine
 * and the UI do not change at all.
 */
object SkillRegistry {

    private val skills = mutableListOf<Skill>()

    fun bootstrap() {
        if (skills.isNotEmpty()) return
        // Order matters: the first skill that claims a command wins, so the
        // specific ones go before the broad ones.
        register(CallSkill())
        register(WhatsAppSkill())
        register(FlashlightSkill())
        register(VolumeSkill())
        register(MediaSkill())
        register(DeviceInfoSkill())
        register(SettingsSkill())
        register(OpenAppSkill())
        register(SpeakSkill())
    }

    fun register(skill: Skill) {
        skills.removeAll { it.id == skill.id }
        skills += skill
    }

    fun unregister(id: String) {
        skills.removeAll { it.id == id }
    }

    /** First skill that claims the command, or null if TOCO has no module yet. */
    fun resolve(command: String): Skill? =
        skills.firstOrNull { it.canHandle(command) }

    fun all(): List<Skill> = skills.toList()

    fun count(): Int = skills.size
}
