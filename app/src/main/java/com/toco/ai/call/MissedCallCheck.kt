package com.toco.ai.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.app.KeyguardManager
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice

/**
 * Handles a missed call without any service at all.
 *
 * The earlier design kept a foreground service alive waiting for the user to
 * unlock. Two things were wrong with that. Since Android 12 a background app
 * may not start a foreground service, so with TOCO closed the service was
 * refused and nothing happened — which is exactly the "only works when the app
 * is open" behaviour. And a service sitting idle for hours is a battery cost
 * for something that needs a few seconds of work.
 *
 * What replaces it:
 *
 *   Notification   posted straight from this process. Always works, with TOCO
 *                  open or closed, and survives the process dying.
 *   Card + speech  shown immediately IF the user is already looking at the
 *                  phone. No waiting, so nothing has to stay alive.
 *   Locked phone   the notification is already on the lock screen. Tapping it
 *                  opens TOCO with the card.
 *
 * The one thing genuinely given up is announcing at the moment of unlock while
 * TOCO is closed. That required a live process, and no amount of code gets
 * around it — the honest trade is the notification, which the user asked for
 * and which costs nothing.
 */
object MissedCallCheck {

    private val handler = Handler(Looper.getMainLooper())

    /** A ring lasts roughly this long, and the log entry lands just after. */
    private val CHECK_DELAYS_MS = listOf(20_000L, 45_000L, 90_000L)

    fun scheduleAfterCall(context: Context) {
        val app = context.applicationContext

        handler.removeCallbacksAndMessages(TOKEN)
        CHECK_DELAYS_MS.forEach { delay ->
            handler.postAtTime({ check(app) }, TOKEN, android.os.SystemClock.uptimeMillis() + delay)
        }
    }

    /** Run now — used when the user opens TOCO and might have missed calls. */
    fun checkNow(context: Context) = check(context.applicationContext)

    private fun check(app: Context) {
        val prefs = Prefs(app)
        if (!prefs.missedCallAlerts) return

        val calls = MissedCallReader.since(app, prefs.lastMissedCallSeen)
        if (calls.isEmpty()) return

        EventLog.log(app, "MISSED", "found " + calls.size + " unannounced")

        // The notification first, because it is the part that always works.
        MissedCallNotifier.post(app, calls)

        if (!isUserPresent(app)) {
            EventLog.log(app, "MISSED", "screen off or locked - notification only")
            return
        }

        // Mark as seen only once it has actually been shown to the user.
        prefs.lastMissedCallSeen = calls.maxOf { it.time }

        val card = MissedCallOverlay(app)
        if (card.canShow()) {
            card.show(calls)
        } else {
            EventLog.log(app, "MISSED", "no overlay permission - notification only")
        }

        if (prefs.voiceReplies) {
            Voice.speak(app, MissedCallReader.announcement(calls))
        }
    }

    /** Screen on and unlocked: there is someone there to show it to. */
    private fun isUserPresent(app: Context): Boolean {
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguard = app.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        val awake = try {
            power?.isInteractive ?: false
        } catch (e: Exception) {
            false
        }

        val locked = try {
            keyguard?.isKeyguardLocked ?: false
        } catch (e: Exception) {
            false
        }

        return awake && !locked
    }

    private val TOKEN = Any()
}
