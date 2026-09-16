package com.toco.ai.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs

/**
 * How TOCO learns about an incoming call on phones that refuse to deliver the
 * PHONE_STATE broadcast.
 *
 * The difference matters. A manifest broadcast receiver is something the app
 * asks to be told about, and OEM skins — Infinix and Tecno especially — simply
 * withhold it from apps that are not on the auto-start list. There is no code
 * fix for that. A CallScreeningService is the opposite arrangement: the system
 * binds it for every incoming call because TOCO holds the screening role, so
 * auto-start and battery managers do not get a say.
 *
 * TOCO allows every call through, unchanged. This is used purely to be told
 * that the phone is ringing and by whom — nothing is blocked, rejected or
 * silenced, and the user's dialer behaves exactly as before.
 */
class TocoCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart

        // Record that the system reached us, which is what the diagnostic
        // reads to tell "no event" apart from "event but nothing happened".
        val prefs = Prefs(applicationContext)
        prefs.lastPhoneEvent = System.currentTimeMillis()
        prefs.lastPhoneState = "SCREENING"

        EventLog.log(
            applicationContext,
            "SCREENING",
            "system invoked us, number=" + (if (number.isNullOrBlank()) "withheld" else number)
        )

        if (prefs.missedCallAlerts) {
            IncomingCallAnnouncer.onRinging(applicationContext, number)

            // The screening service is unbound as soon as this returns, so it
            // cannot itself wait to see whether the call is answered. The
            // watcher takes over: it checks the call log shortly afterwards and
            // again at the next unlock.
            try {
                startService(
                    Intent(this, CallWatcherService::class.java)
                        .setAction(CallWatcherService.ACTION_INCOMING)
                )
            } catch (e: Exception) {
                EventLog.log(applicationContext, "SCREENING", "watcher start refused: " + e.message)
            }
        }

        // Allow the call. Every disallow flag stays false on purpose.
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
