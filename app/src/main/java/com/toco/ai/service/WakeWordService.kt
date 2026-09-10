package com.toco.ai.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.engine.CommandEngine
import com.toco.ai.skill.SkillResult
import com.toco.ai.ui.MainActivity
import com.toco.ai.util.Permissions

/**
 * Always-on wake word by looping SpeechRecognizer.
 *
 * BE CLEAR ABOUT WHAT THIS IS. SpeechRecognizer is designed for short bursts,
 * not continuous listening, so this restarts it endlessly. Consequences that
 * are inherent to the approach and not bugs:
 *
 *   - Noticeable battery drain. The mic and the recognizer never rest.
 *   - Missed wake words during the gap between restarts.
 *   - It holds the mic, so other apps (including Google Assistant) may fail
 *     to record while TOCO is listening, and vice versa.
 *   - Some recognizer implementations require network, so detection can stop
 *     working when offline.
 *   - Aggressive OEM battery managers (Infinix included) will kill this
 *     service unless the app is set to Unrestricted.
 *
 * A trained on-device wake-word model is the correct fix; this is the honest
 * version of what's possible without adding that dependency.
 *
 * Backoff matters: a recognizer that errors instantly would otherwise spin in
 * a tight loop and cook the battery in minutes.
 */
class WakeWordService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val engine = CommandEngine()

    private var running = false
    private var awaitingCommand = false
    private var consecutiveErrors = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TALK -> {
                // Tile or notification tap: skip the wake word, listen now.
                startForeground(NOTIFICATION_ID, notification(listeningForCommand = true))
                awaitingCommand = true
                restartListening(0)
                return START_STICKY
            }
        }

        if (!Permissions.has(this, Manifest.permission.RECORD_AUDIO)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification(listeningForCommand = false))

        if (!running) {
            running = true
            restartListening(0)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    // ---------------- listening loop ----------------

    private fun restartListening(delayMs: Long) {
        if (!running && !awaitingCommand) return

        handler.postDelayed({
            if (!running && !awaitingCommand) return@postDelayed
            listenOnce()
        }, delayMs)
    }

    private fun listenOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }

        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: arrayListOf()

                consecutiveErrors = 0
                handleHeard(heard)
            }

            override fun onError(error: Int) {
                // NO_MATCH and SPEECH_TIMEOUT are normal in a loop — silence.
                val benign = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                if (!benign) consecutiveErrors++

                if (consecutiveErrors >= MAX_ERRORS) {
                    // Something is persistently wrong; stop rather than spin.
                    Voice.speak(this@WakeWordService, "Wake word listening stopped.")
                    stopSelf()
                    return
                }

                restartListening(if (benign) SHORT_GAP_MS else backoffMs())
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        try {
            r.startListening(intent)
        } catch (e: Exception) {
            consecutiveErrors++
            restartListening(backoffMs())
        }
    }

    private fun backoffMs(): Long =
        (SHORT_GAP_MS * (1 shl consecutiveErrors.coerceAtMost(5))).coerceAtMost(30_000L)

    // ---------------- wake word + command ----------------

    private fun handleHeard(heard: List<String>) {
        val prefs = Prefs(this)

        if (awaitingCommand) {
            awaitingCommand = false
            val command = heard.firstOrNull()?.trim()
            if (!command.isNullOrEmpty()) execute(command)
            restartListening(SHORT_GAP_MS)
            return
        }

        val phrases = prefs.wakePhrases()
        val match = heard.firstOrNull { candidate ->
            val lower = candidate.lowercase()
            phrases.any { lower.startsWith(it) || lower.contains(it) }
        }

        if (match == null) {
            restartListening(SHORT_GAP_MS)
            return
        }

        // A wake phrase and a command often arrive together: "toco volume up".
        val trailing = stripWakePhrase(match, phrases)
        if (trailing.isNotEmpty()) {
            execute(trailing)
            restartListening(SHORT_GAP_MS)
            return
        }

        Voice.speak(this, "Yes?")
        awaitingCommand = true
        restartListening(WAKE_REPLY_GAP_MS)
    }

    private fun stripWakePhrase(heard: String, phrases: List<String>): String {
        var text = heard.lowercase().trim()
        for (phrase in phrases.sortedByDescending { it.length }) {
            if (text.startsWith(phrase)) {
                text = text.removePrefix(phrase).trim()
                break
            }
        }
        return text.trim(',', '.', '!', '?').trim()
    }

    /**
     * Runs the command from the service.
     *
     * Actions that need no UI (volume, flashlight, media, spoken answers) work
     * while locked. Actions that must open an activity — launching an app,
     * dialling — are blocked by Android's background-activity-start rules
     * unless TOCO has "Display over other apps". When that happens the failure
     * is reported out loud rather than silently swallowed.
     */
    private fun execute(command: String) {
        val result = try {
            engine.handle(this, command)
        } catch (e: Exception) {
            SkillResult.Failed("That failed: ${e.message}")
        }

        val spoken = when (result) {
            is SkillResult.Ok -> result.message
            is SkillResult.Failed -> result.message
            is SkillResult.NeedsPermission ->
                "I need a permission for that. Open TOCO and tap the lock icon."
            SkillResult.NotHandled ->
                "I can only run device commands while listening in the background."
        }

        Voice.speak(this, spoken)
    }

    // ---------------- notification ----------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wake_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    private fun notification(listeningForCommand: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val talk = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_TALK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.wake_title))
            .setContentText(
                getString(
                    if (listeningForCommand) R.string.wake_listening else R.string.wake_idle
                )
            )
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(action(getString(R.string.wake_action_talk), talk))
            .addAction(action(getString(R.string.wake_action_stop), stop))
            .build()
    }

    /**
     * Built separately because Notification.Action.Builder has both an
     * Icon and a deprecated int-icon overload; a bare null is ambiguous, so
     * the type is stated explicitly.
     */
    private fun action(title: String, intent: PendingIntent): Notification.Action {
        val icon: Icon? = null
        return Notification.Action.Builder(icon, title, intent).build()
    }

    companion object {
        const val ACTION_TALK = "com.toco.ai.WAKE_TALK"
        const val ACTION_STOP = "com.toco.ai.WAKE_STOP"

        private const val CHANNEL_ID = "toco_wake"
        private const val NOTIFICATION_ID = 42

        /** Gap between recognizer sessions. Shorter burns battery faster. */
        private const val SHORT_GAP_MS = 400L

        /** Time for "Yes?" to finish before listening for the command. */
        private const val WAKE_REPLY_GAP_MS = 1200L

        /** Give up after this many non-benign errors in a row. */
        private const val MAX_ERRORS = 8

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WakeWordService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
