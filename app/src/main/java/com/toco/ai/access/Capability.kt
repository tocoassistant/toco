package com.toco.ai.access

/**
 * One thing TOCO might be able to do, and what Android requires to allow it.
 *
 * [Gate] records HOW access is obtained, which is the difference between a
 * capability TOCO can request itself and one the user must grant in system
 * settings. That distinction is shown in the UI rather than hidden.
 */
enum class Gate {
    /** A runtime permission TOCO can ask for with a dialog. */
    RUNTIME,

    /** Only grantable by the user in a system settings screen. */
    SYSTEM_SETTINGS,

    /** Available with no permission at all. */
    ALWAYS
}

data class Capability(
    val id: String,
    val label: String,
    val explanation: String,
    val gate: Gate,
    /** Runtime permissions required, empty for non-RUNTIME gates. */
    val permissions: List<String> = emptyList(),
    /** Minimum API level this capability exists on, or 0 for all. */
    val minSdk: Int = 0
)
