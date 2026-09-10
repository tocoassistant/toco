package com.toco.ai.ai

sealed class AIResult {
    data class Ok(val text: String) : AIResult()
    data class Failed(val message: String) : AIResult()

    /** Key missing or rejected — distinct from a network failure so the UI can guide setup. */
    data class NotConfigured(val message: String) : AIResult()
}
