package com.toco.ai.ui.home

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.core.VoiceInput
import com.toco.ai.ai.Ai
import com.toco.ai.engine.CommandEngine
import com.toco.ai.engine.CommandSequencer
import com.toco.ai.skill.SkillResult
import com.toco.ai.ui.MainActivity
import com.toco.ai.ui.common.ChooserDialog
import com.toco.ai.ui.access.AccessSheet
import com.toco.ai.ui.widget.OrbView
import com.toco.ai.util.Permissions
import java.util.Calendar

class HomeFragment : Fragment() {

    private lateinit var orb: OrbView
    private lateinit var tvGreeting: TextView
    private lateinit var tvSubGreeting: TextView
    private lateinit var tvOrbState: TextView
    private lateinit var commandBar: View
    private lateinit var topBar: View
    private lateinit var etCommand: EditText

    private val engine = CommandEngine()
    private val sequencer = CommandSequencer(engine)

    /**
     * The command that triggered a permission request. Without this, granting
     * the permission did nothing visible and the feature looked broken — the
     * user had to retype the command.
     */
    private var pendingCommand: String? = null
    private lateinit var prefs: Prefs
    private var voiceInput: VoiceInput? = null

    /** Mic permission, requested only when the user first taps to talk. */
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) beginListening() else toast("Microphone permission denied.")
        }

    /** Permissions a skill asked for mid-command (call, contacts, ...). */
    private val skillPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val retry = pendingCommand
            pendingCommand = null
            if (granted && retry != null) {
                dispatch(retry)
            } else if (!granted) {
                toast("Permission denied.")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = Prefs(requireContext())
        voiceInput = VoiceInput(requireContext())

        bindViews(view)
        renderGreeting()
        wireInput()

        // Only animate on first creation, not when returning to this tab.
        if (savedInstanceState == null) playEntrance()
    }

    override fun onDestroyView() {
        voiceInput?.destroy()
        voiceInput = null
        super.onDestroyView()
    }

    // ---------------- setup ----------------

    private fun bindViews(root: View) {
        topBar = root.findViewById(R.id.topBar)
        orb = root.findViewById(R.id.orbView)
        tvGreeting = root.findViewById(R.id.tvGreeting)
        tvSubGreeting = root.findViewById(R.id.tvSubGreeting)
        tvOrbState = root.findViewById(R.id.tvOrbState)
        commandBar = root.findViewById(R.id.commandBar)
        etCommand = root.findViewById(R.id.etCommand)
    }

    private fun renderGreeting() {
        val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> getString(R.string.greet_morning)
            in 12..16 -> getString(R.string.greet_afternoon)
            else -> getString(R.string.greet_evening)
        }
        tvGreeting.setText("$greeting, ${prefs.userName}.")
    }

    private fun wireInput() {
        orb.setOnClickListener { toggleListening() }
        requireView().findViewById<View>(R.id.btnMic).setOnClickListener { toggleListening() }
        requireView().findViewById<View>(R.id.btnSend).setOnClickListener { submitTyped() }

        requireView().findViewById<View>(R.id.btnPermissions).setOnClickListener {
            AccessSheet().show(parentFragmentManager, AccessSheet.TAG)
        }

        etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitTyped()
                true
            } else {
                false
            }
        }
    }

    // ---------------- voice ----------------

    private fun toggleListening() {
        if (orb.currentState() == OrbView.State.LISTENING) {
            voiceInput?.stop()
            idle()
            return
        }

        if (!Permissions.has(requireContext(), Manifest.permission.RECORD_AUDIO)) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        beginListening()
    }

    private fun beginListening() {
        val input = voiceInput ?: return

        orb.setState(OrbView.State.LISTENING)
        setLabel(R.string.orb_listening)

        input.start(object : VoiceInput.Callback {
            override fun onReady() {
                if (isAdded) setLabel(R.string.orb_listening)
            }

            override fun onResult(text: String) {
                if (!isAdded) return
                etCommand.setText(text)
                dispatch(text)
            }

            override fun onError(message: String) {
                if (!isAdded) return
                tvOrbState.setText(message)
                orb.setState(OrbView.State.IDLE)
                resetSoon()
            }
        })
    }

    // ---------------- command flow ----------------

    private fun submitTyped() {
        val text = etCommand.getText().toString()
        if (text.isBlank()) return
        dispatch(text)
    }

    /**
     * Device commands run inline. Anything else goes to the model on a
     * background thread, because a network call on the main thread would
     * freeze the UI (and Android would throw).
     */
    private fun dispatch(command: String) {
        etCommand.setText("")

        val steps = sequencer.split(command)

        if (steps.size > 1) {
            runSequence(command, steps.size)
            return
        }

        if (engine.isDeviceCommand(command)) {
            pendingCommand = command
            orb.setState(OrbView.State.WORKING)
            setLabel(R.string.orb_working)
            applyAction(engine.handle(requireContext(), command))
            return
        }

        if (!Ai.isReady()) {
            tvOrbState.setText(getString(R.string.no_module))
            orb.setState(OrbView.State.IDLE)
            resetSoon()
            return
        }

        orb.setState(OrbView.State.WORKING)
        setLabel(R.string.orb_thinking)

        Thread {
            val reply = engine.handleWithAi(requireContext(), command)
            view?.post {
                if (!isAdded) return@post
                when (reply) {
                    is CommandEngine.Reply.Action -> applyAction(reply.result)
                    is CommandEngine.Reply.Answer -> {
                        tvOrbState.setText(reply.text)
                        if (prefs.voiceReplies) Voice.speak(requireContext(), reply.text)
                        orb.setState(OrbView.State.IDLE)
                    }
                    is CommandEngine.Reply.Unavailable -> {
                        tvOrbState.setText(reply.message)
                        orb.setState(OrbView.State.IDLE)
                        resetSoon()
                    }
                }
            }
        }.start()
    }

    /**
     * Chained commands run on a background thread because the sequencer sleeps
     * between steps to let each app reach the foreground.
     */
    private fun runSequence(command: String, total: Int) {
        orb.setState(OrbView.State.WORKING)
        tvOrbState.setText(getString(R.string.step_progress, 1, total))

        Thread {
            sequencer.run(
                context = requireContext(),
                raw = command,
                onStep = { index, count, step ->
                    view?.post {
                        if (!isAdded) return@post
                        tvOrbState.setText(
                            getString(R.string.step_progress, index + 1, count) + "  " + step
                        )
                    }
                },
                onDone = { done ->
                    view?.post {
                        if (!isAdded) return@post
                        val failed = done.lastOrNull()?.result
                        if (failed is SkillResult.Ok || failed == null) {
                            val summary = getString(R.string.steps_done, done.size)
                            tvOrbState.setText(summary)
                            if (prefs.voiceReplies) Voice.speak(requireContext(), summary)
                        } else {
                            val message = when (failed) {
                                is SkillResult.Failed -> failed.message
                                is SkillResult.NeedsPermission -> failed.reason
                                else -> getString(R.string.no_module)
                            }
                            tvOrbState.setText(
                                getString(R.string.step_stopped, done.size) + " " + message
                            )
                        }
                        orb.setState(OrbView.State.IDLE)
                        resetSoon()
                    }
                }
            )
        }.start()
    }

    private fun applyAction(result: SkillResult) {
        when (result) {
            is SkillResult.Ok -> {
                tvOrbState.setText(result.message)
                if (prefs.voiceReplies) Voice.speak(requireContext(), result.message)
                resetSoon()
            }
            is SkillResult.Failed -> {
                tvOrbState.setText(result.message)
                resetSoon()
            }
            is SkillResult.NeedsPermission -> {
                toast(result.reason)
                skillPermission.launch(result.permission)
                idle()
            }
            is SkillResult.Choose -> {
                idle()
                askWhich(result)
            }
            SkillResult.NotHandled -> {
                tvOrbState.setText(getString(R.string.no_module))
                resetSoon()
            }
        }
    }

    /**
     * Asks which contact was meant. Picking runs the same pipeline again with
     * the number substituted in, so nothing about the command path is special
     * cased for chosen contacts.
     */
    private fun askWhich(choice: SkillResult.Choose) {
        if (!isAdded) return

        // WhatsApp commands get a different button than calls, so the row says
        // what will actually happen.
        val action = if (choice.options.firstOrNull()?.command?.startsWith("whatsapp") == true) {
            getString(R.string.choose_message)
        } else {
            getString(R.string.overlay_call)
        }

        ChooserDialog.show(requireContext(), choice, action) { option ->
            dispatch(option.command)
        }

        tvOrbState.setText(choice.prompt)
    }

    private fun idle() {
        orb.setState(OrbView.State.IDLE)
        setLabel(R.string.orb_idle)
    }

    private fun resetSoon() {
        orb.postDelayed({ if (isAdded) idle() }, RESET_MS)
    }

    private fun setLabel(res: Int) {
        tvOrbState.setText(getString(res))
    }

    private fun toast(message: String) {
        if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // ---------------- entrance animation ----------------

    private fun playEntrance() {
        val all = listOf(topBar, orb, tvGreeting, tvSubGreeting, tvOrbState, commandBar)
        all.forEach { it.alpha = 0f }

        orb.scaleX = 0.6f
        orb.scaleY = 0.6f
        commandBar.translationY = 80f

        topBar.animate().alpha(1f).setDuration(400).setStartDelay(100).start()

        orb.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600).setStartDelay(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        tvGreeting.animate().alpha(1f).setDuration(400).setStartDelay(450).start()
        tvSubGreeting.animate().alpha(1f).setDuration(400).setStartDelay(550).start()
        tvOrbState.animate().alpha(1f).setDuration(400).setStartDelay(650).start()

        commandBar.animate()
            .alpha(1f).translationY(0f)
            .setDuration(450).setStartDelay(700)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private companion object {
        const val RESET_MS = 2600L
    }
}
