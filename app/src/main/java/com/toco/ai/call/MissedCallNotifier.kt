package com.toco.ai.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.ui.MainActivity

/**
 * Builds and posts the missed-call notification.
 *
 * Split out from the watcher service so the same notification can be posted
 * from two places: immediately when the call is missed (for the lock screen),
 * and again at unlock alongside the spoken announcement.
 *
 * VISIBILITY_PUBLIC is what makes the names readable on the lock screen
 * instead of being hidden behind "contents hidden".
 */
object MissedCallNotifier {

    const val CHANNEL_ALERT = "toco_missed_alert"
    const val ALERT_ID = 52

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.missed_channel_alert),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    /** Called right after a call is missed; reads whatever the log now holds. */
    fun notifyLatest(context: Context) {
        val since = Prefs(context).lastMissedCallSeen
        val calls = MissedCallReader.since(context, since)
        if (calls.isNotEmpty()) post(context, calls)
    }

    fun post(context: Context, calls: List<MissedCall>) {
        if (calls.isEmpty()) return
        ensureChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_ALERT)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val title = if (calls.size == 1) {
            context.getString(R.string.missed_one)
        } else {
            context.getString(R.string.missed_many, calls.size)
        }

        val notification = builder
            .setContentTitle(title)
            .setContentText(calls.last().label())
            .setStyle(
                Notification.BigTextStyle()
                    .bigText(MissedCallReader.notificationText(calls))
            )
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentIntent(open)
            .setAutoCancel(true)
            // Readable on the lock screen, which is the point of posting early.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        manager.notify(ALERT_ID, notification)
    }
}
