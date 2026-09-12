package com.toco.ai.ui.settings

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.toco.ai.R
import com.toco.ai.ai.Ai
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.call.CallWatcherService
import com.toco.ai.call.MissedCallReader
import com.toco.ai.service.WakeWordService
import com.toco.ai.util.Permissions

/**
 * Settings: the switches that change how TOCO behaves.
 *
 * The wake-word toggle actually starts and stops the service rather than only
 * writing a preference, so what the screen says matches what is running.
 */
class SettingsFragment : Fragment() {

    private lateinit var prefs: Prefs

    private val callLogPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableMissedAlerts(true)
            } else {
                toast("Missed call alerts need call log access.")
            }
            renderToggles(requireView())
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableWake(true)
            } else {
                toast("Background listening needs microphone access.")
            }
            renderToggles(requireView())
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        view.findViewById<TextView>(R.id.tvWakeNote).setText(getString(R.string.wake_note))

        val input = view.findViewById<EditText>(R.id.etWakeWords)
        input.setText(prefs.wakeWords)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveWakeWords(input.getText().toString())
                true
            } else {
                false
            }
        }

        renderToggles(view)
    }

    override fun onPause() {
        super.onPause()
        // Save on leaving, so an edit isn't lost by switching tabs.
        view?.findViewById<EditText>(R.id.etWakeWords)?.let {
            saveWakeWords(it.getText().toString())
        }
    }

    private fun saveWakeWords(raw: String) {
        val cleaned = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        if (cleaned.isEmpty()) return
        if (cleaned == prefs.wakeWords) return

        prefs.wakeWords = cleaned

        // Restart so the new phrases take effect immediately.
        if (prefs.wakeEnabled) {
            WakeWordService.stop(requireContext())
            WakeWordService.start(requireContext())
        }
    }

    private fun renderToggles(root: View) {
        val list = root.findViewById<LinearLayout>(R.id.toggleList)
        val inflater = LayoutInflater.from(requireContext())
        list.removeAllViews()

        addToggle(
            list, inflater,
            label = getString(R.string.wake_toggle),
            description = getString(R.string.wake_toggle_desc),
            on = prefs.wakeEnabled
        ) {
            if (prefs.wakeEnabled) {
                enableWake(false)
                renderToggles(root)
            } else if (Permissions.has(requireContext(), Manifest.permission.RECORD_AUDIO)) {
                enableWake(true)
                renderToggles(root)
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        addToggle(
            list, inflater,
            label = getString(R.string.missed_toggle),
            description = getString(R.string.missed_toggle_desc),
            on = prefs.missedCallAlerts,
            onLongClick = { diagnoseMissedCalls() }
        ) {
            if (prefs.missedCallAlerts) {
                enableMissedAlerts(false)
                renderToggles(root)
            } else if (Permissions.has(requireContext(), Manifest.permission.READ_CALL_LOG)) {
                enableMissedAlerts(true)
                renderToggles(root)
            } else {
                callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
            }
        }

        // Read-only row: makes a missing key obvious instead of surfacing as
        // "I have no module for that" during a conversation.
        addToggle(
            list, inflater,
            label = getString(R.string.ai_status),
            description = getString(
                if (Ai.isReady()) R.string.ai_ready_desc else R.string.ai_missing_desc
            ),
            on = Ai.isReady()
        ) {
            toast(
                if (Ai.isReady()) "Gemini key is present in this build."
                else "No key in this build. Add GEMINI_API_KEY as a GitHub secret."
            )
        }

        addToggle(
            list, inflater,
            label = getString(R.string.loud_voice_toggle),
            description = getString(R.string.loud_voice_desc),
            on = prefs.loudVoice
        ) {
            prefs.loudVoice = !prefs.loudVoice
            renderToggles(root)
            Voice.speak(requireContext(), "This is how loud I will be.")
        }

        addToggle(
            list, inflater,
            label = getString(R.string.voice_replies_toggle),
            description = getString(R.string.voice_replies_desc),
            on = prefs.voiceReplies
        ) {
            prefs.voiceReplies = !prefs.voiceReplies
            renderToggles(root)
        }
    }

    /**
     * Reports exactly why missed-call alerts are or aren't working, instead of
     * leaving "nothing happened" to be guessed at. Checks each precondition in
     * the order it would actually fail.
     */
    private fun diagnoseMissedCalls() {
        val context = requireContext()

        if (!prefs.missedCallAlerts) {
            toast("Missed call alerts are OFF. Tap the row to turn them on.")
            return
        }

        if (!Permissions.has(context, Manifest.permission.READ_CALL_LOG)) {
            toast("Call log permission is missing. Tap the row to grant it.")
            return
        }

        // Look back a day regardless of what has already been announced, so
        // the check works even after an alert was shown.
        val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val recent = MissedCallReader.since(context, dayAgo)

        if (recent.isEmpty()) {
            toast("Working, but no missed calls in the last 24 hours to report.")
            return
        }

        val ring = Voice.ringVolumePercent(context)
        val volumeNote = if (ring == 0) " Ring volume is 0, so you will see it but not hear it." else ""

        toast("Found " + recent.size + " missed call(s) in 24h. Speaking now." + volumeNote)
        Voice.speak(context, MissedCallReader.announcement(recent))
    }

    private fun addToggle(
        parent: LinearLayout,
        inflater: LayoutInflater,
        label: String,
        description: String,
        on: Boolean,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
    ) {
        val row = inflater.inflate(R.layout.item_toggle, parent, false)

        row.findViewById<TextView>(R.id.toggleLabel).setText(label)
        row.findViewById<TextView>(R.id.toggleDesc).setText(description)

        val state = row.findViewById<TextView>(R.id.toggleState)
        state.setText(getString(if (on) R.string.state_on else R.string.state_off))
        state.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (on) R.color.status_ok else R.color.text_hint
            )
        )

        row.setOnClickListener { onClick() }
        if (onLongClick != null) {
            row.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        parent.addView(row)
    }

    private fun enableWake(enabled: Boolean) {
        prefs.wakeEnabled = enabled

        if (enabled) {
            WakeWordService.start(requireContext())
            toast("Listening. Set TOCO to Unrestricted in battery settings so it survives.")
        } else {
            WakeWordService.stop(requireContext())
        }
    }

    private fun enableMissedAlerts(enabled: Boolean) {
        prefs.missedCallAlerts = enabled

        if (enabled) {
            // Only report calls from now on, not the entire call history.
            prefs.lastMissedCallSeen = System.currentTimeMillis()
            CallWatcherService.start(requireContext())
            toast("TOCO will tell you about missed calls when you unlock.")
        } else {
            CallWatcherService.stop(requireContext())
        }
    }

    private fun toast(message: String) {
        if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}
