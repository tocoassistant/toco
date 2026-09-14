package com.toco.ai.call

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.util.Contacts
import com.toco.ai.util.Permissions

/**
 * Says who is calling while the phone is still ringing.
 *
 * Volume handling is the whole point here. TOCO speaks on the ring stream, so
 * without intervention its voice and the ringtone fight at the same level and
 * neither is intelligible. Instead the ringtone is ducked to 80% for the
 * length of the announcement and restored immediately after, which is what
 * makes the name audible over it.
 *
 * The 2 second delay is deliberate: announcing on the very first instant of
 * ringing talks over the start of the ringtone and sounds like a glitch.
 *
 * Nothing here can stop, answer or silence the call — Android does not permit
 * that from an ordinary app, and the announcement is purely additive.
 */
object IncomingCallAnnouncer {

    private val handler = Handler(Looper.getMainLooper())

    /** Volume to restore once the announcement is done. */
    private var duckedFrom: Int? = null

    /** Guards against the ring state being broadcast more than once. */
    private var announcedFor: String? = null

    fun onRinging(context: Context, number: String?) {
        val prefs = Prefs(context)
        if (!prefs.missedCallAlerts || !prefs.voiceReplies) return

        val key = number ?: "unknown"
        if (announcedFor == key) return
        announcedFor = key

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ announce(context, number) }, ANNOUNCE_DELAY_MS)
    }

    /** Ringing stopped — restore volume and allow the next call to announce. */
    fun onStopped(context: Context) {
        announcedFor = null
        handler.removeCallbacksAndMessages(null)
        restore(context)
    }

    private fun announce(context: Context, number: String?) {
        val who = describe(context, number)
        duck(context)
        Voice.speak(context, "Call from $who")

        // Restore on a timer: TTS completion is not reported consistently
        // across engines, and a permanently ducked ringtone is a worse bug
        // than one that comes back a second early.
        handler.postDelayed({ restore(context) }, RESTORE_AFTER_MS)
    }

    private fun describe(context: Context, number: String?): String {
        if (number.isNullOrBlank()) return "an unknown number"

        if (Permissions.has(context, android.Manifest.permission.READ_CONTACTS)) {
            Contacts.findByNumber(context, number)?.let { return it }
        }

        // Read digits individually; "one seven two seven" is far easier to
        // catch than a TTS engine reading a long number as one huge value.
        return number.filter { it.isDigit() || it == '+' }.toCharArray().joinToString(" ")
    }

    private fun duck(context: Context) {
        if (duckedFrom != null) return

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
            val current = audio.getStreamVolume(AudioManager.STREAM_RING)
            duckedFrom = current

            val ducked = (max * DUCK_PERCENT / 100).coerceAtLeast(1)
            if (ducked < current) {
                audio.setStreamVolume(AudioManager.STREAM_RING, ducked, 0)
            }
        } catch (e: Exception) {
            // Do Not Disturb blocks volume changes without policy access.
            duckedFrom = null
        }
    }

    private fun restore(context: Context) {
        val previous = duckedFrom ?: return
        duckedFrom = null

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audio?.setStreamVolume(AudioManager.STREAM_RING, previous, 0)
        } catch (e: Exception) {
            // Nothing useful left to try.
        }
    }

    private const val ANNOUNCE_DELAY_MS = 2000L
    private const val RESTORE_AFTER_MS = 6000L

    /** Ringtone level during the announcement. */
    private const val DUCK_PERCENT = 80
}
