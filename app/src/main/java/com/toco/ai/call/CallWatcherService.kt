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
import android.media.AudioManager
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
    private var overlay: MissedCallOverlay? = null

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
        overlay?.dismiss()
        overlay = null
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

        MissedCallNotifier.post(this, calls)

        overlay?.dismiss()
        overlay = MissedCallOverlay(this).also { it.show(calls) }

        if (prefs.voiceReplies) {
            boostThenSpeak(MissedCallReader.announcement(calls))
        }
    }

    /**
     * Raises ring volume to maximum for the announcement, then puts it back.
     *
     * Without this the announcement is as quiet as whatever the ringer happens
     * to be set to, which defeats the point of speaking at all. The original
     * level is restored on a delay, because TTS gives no reliable "finished"
     * signal across every engine — an unrestored volume would be a far worse
     * bug than an announcement that is briefly loud.
     *
     * Changing ring volume is refused while Do Not Disturb is on unless the
     * app holds notification policy access, so the failure is caught and the
     * announcement still plays at the current level.
     */
    private fun boostThenSpeak(text: String) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        var restore: Int? = null
        try {
            if (audio != null) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
                val current = audio.getStreamVolume(AudioManager.STREAM_RING)
                if (current < max) {
                    restore = current
                    audio.setStreamVolume(AudioManager.STREAM_RING, max, 0)
                }
            }
        } catch (e: Exception) {
            restore = null
        }

        Voice.speak(this, text)

        val previous = restore ?: return
        handler.postDelayed({
            try {
                audio?.setStreamVolume(AudioManager.STREAM_RING, previous, 0)
            } catch (e: Exception) {
                // DND turned on mid-announcement; nothing to do.
            }
        }, RESTORE_VOLUME_MS)
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

        MissedCallNotifier.ensureChannel(this)
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

    companion object {
        const val ACTION_STOP = "com.toco.ai.MISSED_STOP"

        private const val CHANNEL_STATUS = "toco_missed_status"
        private const val NOTIFICATION_ID = 51
        private const val ANNOUNCE_DELAY_MS = 1200L
        private const val RESTORE_VOLUME_MS = 9000L

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
