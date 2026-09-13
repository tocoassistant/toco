package com.toco.ai.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import com.toco.ai.ai.Ai
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.engine.CommandEngine
import com.toco.ai.skill.SkillResult
import com.toco.ai.ui.MainActivity
import com.toco.ai.util.CommandText
import com.toco.ai.util.Permissions

/**
 * Wake-word listening with a real sleep mode.
 *
 * The first version kept SpeechRecognizer running in a permanent loop. That
 * was the wrong design: the recognizer is heavy, restarting it constantly
 * burned battery, and every restart opened a gap where a wake word was missed.
 *
 * This version has two states:
 *
 *   SLEEPING  A tiny AudioRecord reads raw amplitude only. No recognition, no
 *             network, almost no work. It is deaf to words — it only notices
 *             that a sound loud enough to be speech happened.
 *
 *   AWAKE     Sound detected, so the mic is handed to SpeechRecognizer for ONE
 *             session to check for a wake phrase. Match -> ask "How can I help
 *             you?" and listen once more for the command. No match -> straight
 *             back to sleep.
 *
 * So the recognizer runs when someone actually speaks near the phone, not
 * every two seconds forever. Releasing AudioRecord before starting the
 * recognizer matters: both want the mic, and holding one blocks the other.
 *
 * Honest limits that remain:
 *   - Amplitude detection cannot tell speech from a door slamming, so the
 *     recognizer still wakes on loud noise. It just goes back to sleep.
 *   - A word spoken in the first moment of waking can be clipped.
 *   - OEM battery managers still kill foreground services; Infinix needs TOCO
 *     set to Unrestricted.
 *   - A trained wake-word model would beat this. This is the best available
 *     without adding that dependency.
 */
class WakeWordService : Service() {

    private enum class State { SLEEPING, AWAKE }

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val engine = CommandEngine()

    private var state = State.SLEEPING
    private var running = false
    private var awaitingCommand = false
    private var consecutiveErrors = 0

    /** Amplitude watcher thread. Null while the recognizer holds the mic. */
    private var listenerThread: Thread? = null

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
                // Tile or notification tap: skip the wake word entirely.
                if (!goForeground(listeningForCommand = true)) return START_NOT_STICKY
                running = true
                state = State.AWAKE
                awaitingCommand = true
                Voice.speak(this, getString(R.string.wake_greeting))
                handler.postDelayed({ if (running) listenOnce() }, WAKE_REPLY_GAP_MS)
                return START_STICKY
            }
        }

        if (!Permissions.has(this, Manifest.permission.RECORD_AUDIO)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!goForeground(listeningForCommand = false)) return START_NOT_STICKY

        if (!running) {
            running = true
            sleepAndWatch()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        Prefs(this).wakeEnabled = false
        handler.removeCallbacksAndMessages(null)
        listenerThread?.interrupt()
        listenerThread = null
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    // ---------------- sleep mode ----------------

    /**
     * Sleep: watch raw microphone amplitude and nothing else.
     *
     * Runs on its own thread reading small buffers. When several consecutive
     * frames are above the noise floor we treat that as "someone is talking
     * nearby" and hand the mic to the recognizer.
     */
    private fun sleepAndWatch() {
        if (!running) return

        state = State.SLEEPING
        updateNotification(listeningForCommand = false)

        listenerThread?.interrupt()

        val thread = Thread {
            val buffer = ShortArray(FRAME_SIZE)
            var record: AudioRecord? = null

            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuffer <= 0) {
                    // Can't watch amplitude on this device; nothing to fall
                    // back to that wouldn't be the old battery-eating loop.
                    handler.post { failAndStop("Microphone unavailable.") }
                    return@Thread
                }

                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, FRAME_SIZE * 2)
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    handler.post { failAndStop("Couldn't open the microphone.") }
                    return@Thread
                }

                record.startRecording()

                var loudFrames = 0

                while (running && state == State.SLEEPING && !Thread.interrupted()) {
                    val read = record.read(buffer, 0, FRAME_SIZE)
                    if (read <= 0) continue

                    if (amplitude(buffer, read) > SPEECH_THRESHOLD) {
                        loudFrames++
                        if (loudFrames >= FRAMES_TO_WAKE) break
                    } else {
                        loudFrames = 0
                    }
                }
            } catch (e: SecurityException) {
                handler.post { failAndStop("Microphone permission was revoked.") }
                return@Thread
            } catch (e: Exception) {
                handler.post { failAndStop("Listening stopped: " + e.message) }
                return@Thread
            } finally {
                // Must release before the recognizer starts; both want the mic.
                try {
                    record?.stop()
                    record?.release()
                } catch (e: Exception) {
                    // Already released.
                }
            }

            if (running && state == State.SLEEPING) {
                handler.post { wakeUp() }
            }
        }

        listenerThread = thread
        thread.start()
    }

    /** Mean absolute amplitude of a frame, 0..32767. */
    private fun amplitude(buffer: ShortArray, length: Int): Int {
        var total = 0L
        for (i in 0 until length) {
            val v = buffer[i].toInt()
            total += if (v < 0) -v.toLong() else v.toLong()
        }
        return (total / length).toInt()
    }

    // ---------------- awake ----------------

    private fun wakeUp() {
        if (!running) return
        state = State.AWAKE
        listenOnce()
    }

    private fun backToSleep(delayMs: Long = 0L) {
        awaitingCommand = false
        recognizer?.destroy()
        recognizer = null

        if (!running) return

        handler.postDelayed({
            if (running) sleepAndWatch()
        }, delayMs)
    }

    private fun listenOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            failAndStop("No speech recognition on this device.")
            return
        }

        updateNotification(listeningForCommand = awaitingCommand)

        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                consecutiveErrors = 0
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: arrayListOf()
                handleHeard(heard)
            }

            override fun onError(error: Int) {
                // Silence or no match just means it was noise, not speech.
                val benign = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                if (benign) {
                    backToSleep()
                    return
                }

                consecutiveErrors++
                if (consecutiveErrors >= MAX_ERRORS) {
                    failAndStop("Listening stopped after repeated errors.")
                } else {
                    backToSleep(backoffMs())
                }
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
            backToSleep(backoffMs())
        }
    }

    private fun backoffMs(): Long =
        (1000L * (1 shl consecutiveErrors.coerceAtMost(5))).coerceAtMost(30_000L)

    private fun failAndStop(message: String) {
        Voice.speak(this, message)
        stopSelf()
    }

    // ---------------- wake word + command ----------------

    private fun handleHeard(heard: List<String>) {
        val prefs = Prefs(this)

        // Second stage: this IS the command.
        if (awaitingCommand) {
            awaitingCommand = false
            val command = heard.firstOrNull()?.trim()

            if (command.isNullOrEmpty()) {
                Voice.speak(this, "I didn't catch that.")
                backToSleep(SLEEP_DELAY_MS)
            } else {
                execute(command)
            }
            return
        }

        val phrases = prefs.wakePhrases()
        val match = heard.firstOrNull { candidate ->
            val lower = CommandText.normalize(candidate)
            phrases.any { lower == it || lower.startsWith("$it ") }
        }

        if (match == null) {
            // Just noise or someone talking about something else.
            backToSleep()
            return
        }

        // Wake phrase and command often arrive together: "toco volume up".
        val trailing = stripWakePhrase(match, phrases)
        if (trailing.isNotEmpty()) {
            execute(trailing)
            return
        }

        // Wake phrase alone: greet, then listen for the command.
        Voice.speak(this, getString(R.string.wake_greeting))
        awaitingCommand = true
        handler.postDelayed({
            if (running) listenOnce()
        }, WAKE_REPLY_GAP_MS)
    }

    private fun stripWakePhrase(heard: String, phrases: List<String>): String {
        var text = CommandText.normalize(heard)
        for (phrase in phrases.sortedByDescending { it.length }) {
            if (text.startsWith(phrase)) {
                text = text.removePrefix(phrase).trim()
                break
            }
        }
        return text.trim(',', '.', '!', '?').trim()
    }

    /**
     * Runs a command, then goes back to sleep.
     *
     * A device action runs immediately. Anything else is a question, so it goes
     * to Gemini and the answer is spoken. The Gemini call blocks on the
     * network, hence the background thread.
     *
     * While the screen is locked, actions needing no UI (volume, flashlight,
     * media, spoken answers) work. Opening an app or dialling needs "Display
     * over other apps", because Android blocks background activity starts.
     */
    private fun execute(command: String) {
        state = State.AWAKE
        updateNotification(listeningForCommand = false)

        if (engine.isDeviceCommand(command)) {
            val result = try {
                engine.handle(this, command)
            } catch (e: Exception) {
                SkillResult.Failed("That failed: " + e.message)
            }

            Voice.speak(this, spokenFor(result))
            backToSleep(SLEEP_DELAY_MS)
            return
        }

        if (!Ai.isReady()) {
            Voice.speak(this, getString(R.string.wake_no_ai))
            backToSleep(SLEEP_DELAY_MS)
            return
        }

        Thread {
            val reply = try {
                engine.handleWithAi(this, command)
            } catch (e: Exception) {
                CommandEngine.Reply.Unavailable("That failed: " + e.message)
            }

            val spoken = when (reply) {
                is CommandEngine.Reply.Action -> spokenFor(reply.result)
                is CommandEngine.Reply.Answer -> reply.text
                is CommandEngine.Reply.Unavailable -> reply.message
            }

            handler.post {
                Voice.speak(this, spoken)
                backToSleep(SLEEP_DELAY_MS)
            }
        }.start()
    }

    private fun spokenFor(result: SkillResult): String = when (result) {
        is SkillResult.Ok -> result.message
        is SkillResult.Failed -> result.message
        is SkillResult.NeedsPermission ->
            "I need a permission for that. Open TOCO and tap the lock icon."
        SkillResult.NotHandled -> getString(R.string.no_module)
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

    /**
     * Android 14 refuses a microphone foreground service started from the
     * background, and throws rather than returning an error. Unhandled, that
     * takes the whole service down with no explanation — so the failure is
     * caught, recorded, and reported the next time the user checks.
     */
    private fun goForeground(listeningForCommand: Boolean): Boolean =
        try {
            startForeground(NOTIFICATION_ID, notification(listeningForCommand))
            lastError = null
            true
        } catch (e: Exception) {
            lastError = "Couldn't start listening: " + e.message
            Prefs(this).wakeEnabled = false
            stopSelf()
            false
        }

    private fun updateNotification(listeningForCommand: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification(listeningForCommand))
    }

    companion object {
        const val ACTION_TALK = "com.toco.ai.WAKE_TALK"

        /** Why listening stopped, if it did. Null when healthy. */
        var lastError: String? = null
            private set
        const val ACTION_STOP = "com.toco.ai.WAKE_STOP"

        private const val CHANNEL_ID = "toco_wake"
        private const val NOTIFICATION_ID = 42

        /** Pause after speaking a reply, before returning to sleep. */
        private const val SLEEP_DELAY_MS = 1500L

        // --- sleep-mode tuning ---
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 1024

        /**
         * Mean amplitude that counts as possible speech. Lower wakes on quiet
         * noise and drains more; higher misses softly spoken wake words.
         */
        private const val SPEECH_THRESHOLD = 1500

        /** Consecutive loud frames required, so a single click doesn't wake it. */
        private const val FRAMES_TO_WAKE = 3

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
