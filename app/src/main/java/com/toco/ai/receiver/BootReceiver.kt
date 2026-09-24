package com.toco.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs
import com.toco.ai.service.WakeWordService

/**
 * Brings wake-word listening back after a reboot.
 *
 * A foreground service does not survive the phone restarting, so without this
 * someone who turned wake word on would find it silently off every morning.
 * The receiver fires once on boot, checks whether the user actually wants it,
 * and starts it again only then — a reboot must never turn a feature ON that
 * the user had left off.
 *
 * Nothing else is started here. The missed-call path needs no service, and the
 * screening role is restored by the system itself, so this is only about the
 * one feature that genuinely cannot resume on its own.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (!isBoot) return

        if (Prefs(context).wakeEnabled) {
            EventLog.log(context, "BOOT", "restarting wake word")
            try {
                WakeWordService.start(context)
            } catch (e: Exception) {
                EventLog.log(context, "BOOT", "wake restart refused: " + e.message)
            }
        }
    }
}
