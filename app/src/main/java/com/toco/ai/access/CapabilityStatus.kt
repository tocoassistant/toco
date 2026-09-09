package com.toco.ai.access

/**
 * Honest states only. There is deliberately no "enabled" that glosses over
 * a partial grant, and no pretending an unsupported capability works.
 */
enum class CapabilityStatus {
    /** Fully granted. */
    ALLOWED,

    /** Some but not all of the required permissions were granted. */
    LIMITED,

    /** Required but not granted. */
    DENIED,

    /** A system service TOCO is not connected to yet. */
    NOT_CONNECTED,

    /** This Android version cannot provide it at all. */
    UNSUPPORTED
}
