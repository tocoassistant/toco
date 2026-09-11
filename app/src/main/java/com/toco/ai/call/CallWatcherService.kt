package com.toco.ai.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.ui.MainActivity

/**
 * Tells the user about calls they missed, the moment they come back to the phone.
 *
 * Triggered by ACTION_USER_PRESENT (unlock) and ACTION_SCREEN_ON, which is
 * exactly the moment described: phone left charging, calls arrive, user
 * returns and presses power — TOCO speaks and posts a notification.
 *
 * Why a foreground service: since Android 8 these two broadcasts cannot be
 * declared in the manifest. Only a running process can receive them. That is a
 * platform rule, not a design choice — the alternative is telling the user at
 * some later, arbitrary moment, which defeats the feature.
 *
 * Unlike the wake-word service this one never touches the microphone and does
 * no polling, so its battery cost is negligible.
 */
class CallWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Unlock — the user is definitely present and looking.
                Intent.ACTION_USER_PRESENT -> announceSoon()

                // Screen on covers phones with no lock screen set.
                Intent.ACTION_SCREEN_ON -> announceSoon()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_USER_PRESENT)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, statusNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Never registered, or already gone.
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * Small delay before speaking. Unlocking is a busy moment — animations,
     * other apps resuming — and talking instantly feels like a glitch.
     */
    private fun announceSoon() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ announce() }, ANNOUNCE_DELAY_MS)
    }

    private fun announce() {
        val prefs = Prefs(this)
        if (!prefs.missedCallAlerts) return

        val since = prefs.lastMissedCallSeen
        val calls = MissedCallReader.since(this, since)
        if (calls.isEmpty()) return

        // Mark as seen immediately so a second unlock doesn't repeat it.
        prefs.lastMissedCallSeen = calls.maxOf { it.time }

        postMissedNotification(calls)

        if (prefs.voiceReplies) {
            Voice.speak(this, MissedCallReader.announcement(calls))
        }
    }

    // ---------------- notifications ----------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Quiet channel for the "watching" notification the user can't dismiss.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                getString(R.string.missed_channel_status),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
        )

        // The actual alert, which should be noticed.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                getString(R.string.missed_channel_alert),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun builder(channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

    private fun statusNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return builder(CHANNEL_STATUS)
            .setContentTitle(getString(R.string.missed_status_title))
            .setContentText(getString(R.string.missed_status_text))
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun postMissedNotification(calls: List<MissedCall>) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (calls.size == 1) {
            getString(R.string.missed_one)
        } else {
            getString(R.string.missed_many, calls.size)
        }

        val notification = builder(CHANNEL_ALERT)
            .setContentTitle(title)
            .setContentText(calls.last().label())
            .setStyle(
                Notification.BigTextStyle()
                    .bigText(MissedCallReader.notificationText(calls))
            )
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_ID, notification)
    }

    companion object {
        const val ACTION_STOP = "com.toco.ai.MISSED_STOP"

        private const val CHANNEL_STATUS = "toco_missed_status"
        private const val CHANNEL_ALERT = "toco_missed_alert"
        private const val NOTIFICATION_ID = 51
        private const val ALERT_ID = 52
        private const val ANNOUNCE_DELAY_MS = 1200L

        fun start(context: Context) {
            val intent = Intent(context, CallWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CallWatcherService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
