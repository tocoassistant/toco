package com.toco.ai.ui.listening

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.core.VoiceInput
import com.toco.ai.engine.CommandEngine
import com.toco.ai.skill.SkillResult
import com.toco.ai.ui.widget.OrbView
import com.toco.ai.util.Permissions
import com.toco.ai.util.Taps

/**
 * The screen the user sees when they summon TOCO — by the assist gesture, the
 * tile, the notification, or a wake word.
 *
 * It listens once, shows what it heard, runs the command or asks Gemini, and
 * speaks the answer. It draws over the lock screen and turns the screen on, so
 * "hey TOCO" works with the phone face-down on a desk.
 *
 * This is a thin shell over the same CommandEngine the home screen uses; it
 * deliberately shares that one path so a command behaves identically however
 * it was triggered.
 */
class ListeningActivity : AppCompatActivity() {

    private lateinit var orb: OrbView
    private lateinit var stateText: TextView
    private lateinit var heardText: TextView

    private val engine = CommandEngine()
    private var voiceInput: VoiceInput? = null
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and wake the display, so a summon works
        // without the user first unlocking.
        showWhenLockedCompat()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_listening)

        orb = findViewById(R.id.listeningOrb)
        stateText = findViewById(R.id.listeningState)
        heardText = findViewById(R.id.listeningHeard)

        findViewById<TextView>(R.id.listeningClose).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.listeningRoot).setOnClickListener { finish() }
        orb.setOnClickListener {
            // Tap the orb to try again after an error.
            if (Taps.allow("listen-retry")) startListening()
        }

        if (!Permissions.has(this, Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "TOCO needs microphone access.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        voiceInput = VoiceInput(this)
        startListening()
    }

    override fun onDestroy() {
        voiceInput?.destroy()
        voiceInput = null
        super.onDestroy()
    }

    private fun startListening() {
        handled = false
        heardText.text = ""
        orb.setState(OrbView.State.LISTENING)
        stateText.setText(getString(R.string.listen_listening))

        voiceInput?.start(object : VoiceInput.Callback {
            override fun onReady() {
                if (!isFinishing) stateText.setText(getString(R.string.listen_listening))
            }

            override fun onResult(text: String) {
                if (isFinishing) return
                heardText.text = text
                dispatch(text)
            }

            override fun onError(message: String) {
                if (isFinishing) return
                stateText.setText(message)
                orb.setState(OrbView.State.IDLE)
                // Leave the orb tappable so the user can retry, then auto-close.
                orb.postDelayed({ if (!isFinishing && !handled) finish() }, AUTO_CLOSE_MS)
            }
        })
    }

    /**
     * A device command runs inline; anything else goes to Gemini on a
     * background thread so the UI never blocks on the network.
     */
    private fun dispatch(command: String) {
        if (handled) return
        handled = true

        orb.setState(OrbView.State.WORKING)

        if (engine.isDeviceCommand(command)) {
            stateText.setText(getString(R.string.listen_working))
            finishWith(engine.handle(this, command))
            return
        }

        stateText.setText(getString(R.string.listen_thinking))

        Thread {
            val reply = engine.handleWithAi(this, command)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                when (reply) {
                    is CommandEngine.Reply.Action -> finishWith(reply.result)
                    is CommandEngine.Reply.Answer -> speakAndClose(reply.text)
                    is CommandEngine.Reply.Unavailable -> speakAndClose(reply.message)
                }
            }
        }.start()
    }

    private fun finishWith(result: SkillResult) {
        val message = when (result) {
            is SkillResult.Ok -> result.message
            is SkillResult.Failed -> result.message
            is SkillResult.NeedsPermission -> result.reason
            is SkillResult.Choose -> result.prompt
            SkillResult.NotHandled -> getString(R.string.no_module)
        }
        speakAndClose(message)
    }

    private fun speakAndClose(message: String) {
        heardText.text = message
        stateText.setText(getString(R.string.listen_speaking))
        orb.setState(OrbView.State.WORKING)

        if (Prefs(this).voiceReplies) Voice.speak(this, message)

        // Give the user a moment to read, then close.
        orb.postDelayed({ if (!isFinishing) finish() }, READ_CLOSE_MS)
    }

    @Suppress("DEPRECATION")
    private fun showWhenLockedCompat() {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private companion object {
        const val AUTO_CLOSE_MS = 4000L
        const val READ_CLOSE_MS = 3500L
    }
}
