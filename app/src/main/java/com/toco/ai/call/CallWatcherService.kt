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
import android.app.KeyguardManager
import android.media.AudioManager
import android.os.PowerManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.ui.MainActivity

/**
 * Waits for the user to come back to the phone after a call was missed, then
 * announces it and stops.
 *
 * This used to run permanently, which is wrong: it sat in Running Apps
 * consuming battery even on days nobody called. Now it is event-driven —
 * started by PhoneStateReceiver only once a call has actually been missed,
 * and it calls stopSelf() the moment it has spoken.
 *
 * So the lifetime is "missed call -> your next unlock", usually a few minutes.
 * The rest of the time TOCO has no running process at all; the manifest
 * receiver costs nothing until the phone rings.
 *
 * A hard timeout stops it anyway if the phone is not unlocked for hours,
 * because a service idling overnight is exactly the problem being fixed. The
 * notification stays regardless, so nothing is lost.
 *
 * Why a service is needed at all: since Android 8, ACTION_USER_PRESENT cannot
 * be declared in the manifest. Only a running process can receive it.
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

        try {
            // Android 14 throws SecurityException unless the export intent is
            // stated explicitly. Omitting it killed this service the moment it
            // started, which silently disabled every missed-call alert.
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            registered = true
        } catch (e: Exception) {
            registered = false
            lastError = "Couldn't listen for unlock: " + e.message
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        // Give up rather than idle indefinitely if the phone is never unlocked.
        handler.postDelayed({ stopSelf() }, MAX_WAIT_MS)

        try {
            startForeground(NOTIFICATION_ID, statusNotification())
            running = true
        } catch (e: Exception) {
            // A foreground service that cannot show its notification is killed
            // by the system anyway; record why before going.
            lastError = "Couldn't start: " + e.message
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        // If the phone is already unlocked and in use, there is nothing to wait
        // for — say it now and shut down.
        if (isUsable()) announceSoon()

        // NOT sticky: if the system kills this, it should stay dead until the
        // next missed call rather than being resurrected to idle again.
        return START_NOT_STICKY
    }

    /** True when the screen is on and the lock screen is not in the way. */
    private fun isUsable(): Boolean {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val awake = power?.isInteractive ?: false
        val locked = keyguard?.isKeyguardLocked ?: false
        return awake && !locked
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
        running = false
        registered = false
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
        overlay = MissedCallOverlay(this).also { card ->
            // Closing the card is what ends the service. Previously the service
            // stopped on a timer and onDestroy tore the card down with it, so
            // the overlay vanished after a few seconds no matter what the user
            // was doing with it.
            card.onClosed = { stopSelf() }
            card.show(calls)
        }

        if (prefs.voiceReplies) {
            boostThenSpeak(MissedCallReader.announcement(calls))
        }

        // Shut down once there is nothing left on screen. If the card is up,
        // the service waits for it — but not forever, or a card left untouched
        // would keep TOCO in Running Apps all day.
        val showing = overlay?.isShowing() == true
        handler.postDelayed(
            {
                overlay?.dismiss()
                stopSelf()
            },
            if (showing) OVERLAY_LIFE_MS else STOP_AFTER_MS
        )
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

        /**
         * Live state, so the settings screen can report whether this is
         * actually running instead of assuming the preference reflects reality.
         */
        var running = false
            private set

        var registered = false
            private set

        /** Why it failed, if it did. Null when healthy. */
        var lastError: String? = null
            private set

        private const val CHANNEL_STATUS = "toco_missed_status"
        private const val NOTIFICATION_ID = 51
        private const val ANNOUNCE_DELAY_MS = 1200L
        private const val RESTORE_VOLUME_MS = 9000L

        /** Grace period after speaking, so the announcement isn't cut off. */
        private const val STOP_AFTER_MS = 12_000L

        /** How long an untouched card stays before closing itself. */
        private const val OVERLAY_LIFE_MS = 2L * 60 * 1000

        /** Longest this will ever wait for an unlock before giving up. */
        private const val MAX_WAIT_MS = 2L * 60 * 60 * 1000

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
