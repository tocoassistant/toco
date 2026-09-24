package com.toco.ai.audio

/**
 * Decides whether a frame of audio is someone speaking close to the phone.
 *
 * A phone cannot measure how far away a voice is. What it can measure is how
 * loud the voice arrives, and loudness falls off sharply with distance — twice
 * as far away is roughly a quarter as loud. So a loudness threshold is a fair
 * stand-in for range: set it low and distant voices get through, set it high
 * and only someone close does.
 *
 * The threshold here is not fixed. It tracks the room's own background level
 * and sits a margin above it:
 *
 *   quiet room   background low  -> threshold low  -> reaches further
 *   noisy room   background high -> threshold high -> close speech only
 *
 * That is what makes TOCO ignore a television across the room without going
 * deaf in silence. It is a loudness rule, not a distance rule, and the limits
 * follow from that: someone shouting from far away can still get through, and
 * someone whispering right next to the phone may not.
 *
 * Kept free of Android types so the decision logic can be exercised directly.
 */
class NoiseGate(
    /** Never let the threshold fall below this, or silence hiss would trigger. */
    private val minThreshold: Int = 900,
    /** Or rise above this, or even a close voice could never get through. */
    private val maxThreshold: Int = 9000,
    /** How far above background a sound must be to count as speech. */
    private val margin: Double = 2.6,
    /** Consecutive loud frames needed, so a single click does not wake it. */
    private val framesToWake: Int = 3
) {
    /** Running estimate of the room's background loudness. */
    var noiseFloor: Double = minThreshold / margin
        private set

    private var loudFrames = 0

    /** The current bar a frame must clear. */
    fun threshold(): Int =
        (noiseFloor * margin).toInt().coerceIn(minThreshold, maxThreshold)

    /**
     * Feed one frame's amplitude. Returns true once enough consecutive frames
     * have cleared the threshold to count as someone speaking.
     */
    /**
     * Frames counted as "loud" in a row. A speech burst is short; sustained
     * loudness is the room, and telling them apart is done by duration.
     */
    private var sustainedLoud = 0

    fun offer(amplitude: Int): Boolean {
        // The floor tracks EVERY frame, above the bar or below it — otherwise a
        // steady loud TV, which always sits above the bar, would never be
        // learned and the gate would treat the whole room as speech. Speech is
        // separated out below by how long it lasts, not by its level.
        val rate = if (amplitude > noiseFloor) RISE else FALL
        noiseFloor += (amplitude - noiseFloor) * rate

        val bar = threshold()

        if (amplitude > bar) {
            loudFrames++

            // A genuine wake word is a brief burst clearly above the room.
            // Something that stays loud for a long time is not a command, it
            // is noise, so stop treating it as a candidate and let the floor
            // catch up to it.
            sustainedLoud++
            if (sustainedLoud > SUSTAINED_LIMIT) {
                loudFrames = 0
                return false
            }

            // Fire once per burst, on the exact frame the count is reached.
            // Firing on every frame after that turned one wake word into a
            // stream of triggers, and let steady noise fire repeatedly.
            return loudFrames == framesToWake
        }

        loudFrames = 0
        sustainedLoud = (sustainedLoud - 1).coerceAtLeast(0)
        return false
    }

    fun reset() {
        loudFrames = 0
    }

    /** Rough label for logs, so a noisy session is recognisable at a glance. */
    fun environment(): String = when {
        threshold() >= maxThreshold -> "very noisy (close speech only)"
        threshold() > minThreshold * 3 -> "noisy (short range)"
        else -> "quiet (full range)"
    }

    private companion object {
        // Floor rises quickly toward new sound, falls back slowly.
        const val RISE = 0.08
        const val FALL = 0.02

        // Frames of continuous loudness after which it is deemed noise, not a
        // command. At this sample rate a wake word is well under this.
        const val SUSTAINED_LIMIT = 40
    }
}
