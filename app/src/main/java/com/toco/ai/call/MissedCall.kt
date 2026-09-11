package com.toco.ai.call

/** One missed call, already resolved to a name where contacts allow it. */
data class MissedCall(
    val number: String,
    /** Contact name, or null when the number isn't saved. */
    val name: String?,
    /** Epoch millis the call arrived. */
    val time: Long
) {
    /** What TOCO says or shows. Unknown numbers are labelled honestly. */
    fun label(): String = name ?: if (number.isBlank()) "an unknown number" else number
}
