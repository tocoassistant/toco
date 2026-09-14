package com.toco.ai.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import com.toco.ai.core.Prefs

/**
 * Posts the notification the moment a call is missed, so it is waiting on the
 * lock screen before the user ever unlocks.
 *
 * The sequence that means "missed" is RINGING and then IDLE with no OFFHOOK in
 * between — the phone rang and nobody answered. Tracking it this way avoids
 * firing on calls the user actually took.
 *
 * The call log is read a moment later rather than immediately, because Android
 * writes the entry slightly after the state change; reading straight away
 * usually returns nothing.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!Prefs(context).missedCallAlerts) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                rang = true
                answered = false
                ringingNumber = intent.getStringExtra("incoming_number")
                IncomingCallAnnouncer.onRinging(context, ringingNumber)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Picked up, so this is not a missed call.
                answered = true
                IncomingCallAnnouncer.onStopped(context)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                IncomingCallAnnouncer.onStopped(context)

                if (rang && !answered) {
                    val appContext = context.applicationContext
                    Handler(Looper.getMainLooper()).postDelayed({
                        MissedCallNotifier.notifyLatest(appContext)

                        // Only now does the watcher need to exist. It waits for
                        // the next unlock, announces, and stops itself — so
                        // TOCO has no running process on days nobody calls.
                        CallWatcherService.start(appContext)
                    }, LOG_WRITE_DELAY_MS)
                }
                rang = false
                answered = false
                ringingNumber = null
            }
        }
    }

    private companion object {
        /**
         * Static because the receiver is recreated for every broadcast, so an
         * instance field would forget that the phone had been ringing.
         */
        var rang = false
        var answered = false
        var ringingNumber: String? = null

        const val LOG_WRITE_DELAY_MS = 1500L
    }
}
