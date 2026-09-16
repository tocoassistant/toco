package com.toco.ai.call

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.util.Contacts
import com.toco.ai.util.Permissions

/**
 * Says who is calling, and keeps saying it until the call is answered or ends.
 *
 * Repeating matters more than it sounds. A single announcement two seconds in
 * is missed by anyone who is in another room, driving, or has just picked the
 * phone up — which is most of the time someone needs it. So the name is
 * repeated at a steady interval for as long as the phone is actually ringing.
 *
 * Knowing when to stop is done by polling the telephony call state rather than
 * by listening for a broadcast, because several manufacturers withhold that
 * broadcast from apps entirely. Polling a single integer every couple of
 * seconds for the duration of one ring is cheap and, unlike the broadcast,
 * always works.
 *
 * Volume: TOCO speaks on the ring stream, so without help its voice and the
 * ringtone sit on top of each other and neither is clear. The ringtone is
 * ducked to 80% while the name plays and restored the instant the call ends.
 * Restoration is defensive — it happens on answer, on hangup, on timeout and
 * on error, because a phone left permanently quiet is a far worse outcome than
 * a missed announcement.
 */
object IncomingCallAnnouncer {

    private val handler = Handler(Looper.getMainLooper())

    /** Ring volume before ducking, or null when not ducked. */
    private var duckedFrom: Int? = null

    /** The call currently being announced, so one ring announces once. */
    private var activeNumber: String? = null

    private var announcements = 0
    private var startedAt = 0L

    /** Cached so repeat announcements don't re-query contacts each time. */
    private var cachedName: String? = null

    // ---------------- entry points ----------------

    fun onRinging(context: Context, number: String?) {
        val app = context.applicationContext
        val prefs = Prefs(app)

        if (!prefs.missedCallAlerts || !prefs.voiceReplies) return

        val key = number?.filter { it.isDigit() }?.ifBlank { null } ?: UNKNOWN
        if (activeNumber == key) return

        // A new call, so clear anything left from the previous one.
        stop(app)

        activeNumber = key
        announcements = 0
        startedAt = System.currentTimeMillis()
        cachedName = describe(app, number)

        // The screening service fires a moment BEFORE the ringtone starts, so
        // announcing on a fixed delay talked over the first ring or arrived
        // early. Wait for the audio mode to actually reach RINGTONE, then add
        // the delay from there.
        waitForRing(app, 0)
    }

    /**
     * Puts the ringtone back if a previous run was killed mid-announcement.
     * Called at startup, because nothing else would ever undo it.
     */
    fun restoreVolumeIfStranded(context: Context) {
        val app = context.applicationContext
        if (Prefs(app).duckedRingVolume >= 0) restore(app)
    }

    /** Call answered, rejected or finished. Safe to call more than once. */
    fun onStopped(context: Context) {
        stop(context.applicationContext)
    }

    private fun stop(app: Context) {
        Voice.stopSpeaking()
        handler.removeCallbacksAndMessages(null)
        activeNumber = null
        announcements = 0
        cachedName = null
        restore(app)
    }

    // ---------------- ringing loop ----------------

    /**
     * Re-checks whether the phone is still ringing. Stops everything the
     * moment it is not, which is what prevents TOCO talking over a call the
     * user has already answered.
     */
    /**
     * Holds off until the phone is genuinely ringing, then starts announcing.
     * Gives up if the ring never materialises, which happens when a call is
     * rejected instantly.
     */
    private fun waitForRing(app: Context, waited: Long) {
        if (activeNumber == null) return

        if (isAnswered(app)) {
            stop(app)
            return
        }

        if (isRinging(app)) {
            EventLog.log(app, "INCOMING", "ringtone detected after " + waited + "ms")
            handler.postDelayed({ announce(app) }, FIRST_DELAY_MS)
            handler.postDelayed({ poll(app) }, POLL_INTERVAL_MS)
            return
        }

        if (waited >= RING_WAIT_TIMEOUT_MS) {
            EventLog.log(app, "INCOMING", "gave up: ringtone never started")
            stop(app)
            return
        }

        handler.postDelayed({ waitForRing(app, waited + POLL_INTERVAL_MS) }, POLL_INTERVAL_MS)
    }

    private fun poll(app: Context) {
        if (activeNumber == null) return

        if (System.currentTimeMillis() - startedAt > MAX_RING_MS) {
            stop(app)
            return
        }

        // Answered or hung up: silence TOCO mid-word rather than letting the
        // sentence run on over a live conversation.
        if (isAnswered(app) || !isRinging(app)) {
            EventLog.log(app, "INCOMING", "call ended or answered - cutting speech")
            Voice.stopSpeaking()
            stop(app)
            return
        }

        handler.postDelayed({ poll(app) }, POLL_INTERVAL_MS)
    }

    /**
     * Is the phone still ringing?
     *
     * Read from the audio mode rather than the telephony call state. On
     * Android 12 and up getCallState() is deprecated and returns IDLE for many
     * apps regardless of permission, which made TOCO think the call had ended
     * after a couple of seconds and stop announcing — the "speaks twice then
     * goes quiet" behaviour.
     *
     * The audio mode needs no permission and is unambiguous:
     *   MODE_RINGTONE          still ringing
     *   MODE_IN_CALL / IN_COMM answered
     *   MODE_NORMAL            over
     */
    private fun isRinging(app: Context): Boolean {
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

        return try {
            audio.mode == AudioManager.MODE_RINGTONE
        } catch (e: Exception) {
            false
        }
    }

    /** True once the call has been picked up. */
    private fun isAnswered(app: Context): Boolean {
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

        return try {
            audio.mode == AudioManager.MODE_IN_CALL ||
                audio.mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            false
        }
    }

    private fun announce(app: Context) {
        if (activeNumber == null) return
        if (!isRinging(app)) {
            stop(app)
            return
        }

        duck(app)
        // Flush rather than queue: if the previous announcement is somehow
        // still playing, repeating on top of it is worse than replacing it.
        Voice.speakNow(app, phrase())

        announcements++
        if (announcements < MAX_ANNOUNCEMENTS) {
            handler.postDelayed({ announce(app) }, REPEAT_EVERY_MS)
        }
    }

    /**
     * "There is an incoming call from Ma."
     * "There is an incoming call from unknown, zero one seven..."
     */
    private fun phrase(): String {
        val who = cachedName ?: return "There is an incoming call."
        return "There is an incoming call from $who."
    }

    private fun describe(app: Context, number: String?): String {
        if (number.isNullOrBlank()) return "an unknown number"

        if (Permissions.has(app, Manifest.permission.READ_CONTACTS)) {
            val saved = try {
                Contacts.findByNumber(app, number)
            } catch (e: Exception) {
                null
            }
            if (!saved.isNullOrBlank()) return saved
        }

        // Not saved: say "unknown" and then the digits one at a time, because
        // a speech engine reading a long number as a single value is useless.
        return "unknown, " + spellOut(number)
    }

    private fun spellOut(number: String): String =
        number.filter { it.isDigit() || it == '+' }
            .map { if (it == '+') "plus" else it.toString() }
            .joinToString(" ")

    // ---------------- volume ----------------

    private fun duck(app: Context) {
        if (duckedFrom != null) return

        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
            if (max <= 0) return

            val current = audio.getStreamVolume(AudioManager.STREAM_RING)
            val target = (max * DUCK_PERCENT / 100).coerceAtLeast(1)

            // Never raise the ringtone, only lower it.
            if (target >= current) return

            duckedFrom = current
            Prefs(app).duckedRingVolume = current
            audio.setStreamVolume(AudioManager.STREAM_RING, target, 0)
        } catch (e: Exception) {
            // Do Not Disturb blocks volume changes without policy access.
            duckedFrom = null
        }
    }

    private fun restore(app: Context) {
        val previous = duckedFrom ?: Prefs(app).duckedRingVolume.takeIf { it >= 0 } ?: return
        duckedFrom = null
        Prefs(app).duckedRingVolume = -1

        try {
            val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.setStreamVolume(AudioManager.STREAM_RING, previous, 0)
        } catch (e: Exception) {
            // Nothing further to try; the user can adjust it manually.
        }
    }

    private const val UNKNOWN = "unknown"

    /** Let the ringtone establish itself before talking over it. */
    private const val FIRST_DELAY_MS = 2000L

    /** Gap between repeats while the phone is still ringing. */
    private const val REPEAT_EVERY_MS = 8000L

    /** How often the call state is re-checked. */
    private const val POLL_INTERVAL_MS = 500L

    /** Stop after this many, so a very long ring is not endless chatter. */
    private const val MAX_ANNOUNCEMENTS = 4

    /** Hard stop, in case the call state can never be read. */
    private const val MAX_RING_MS = 60_000L

    /** How long to wait for the ringtone to start before giving up. */
    private const val RING_WAIT_TIMEOUT_MS = 6000L

    /** Ringtone level while the name is being spoken. */
    private const val DUCK_PERCENT = 80
}
