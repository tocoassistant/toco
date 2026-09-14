package com.toco.ai.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.ui.MainActivity
import com.toco.ai.ui.widget.OrbView
import com.toco.ai.util.Permissions

/**
 * Shown once, on first launch, before the main UI.
 *
 * Two reasons this exists rather than asking for things as they are needed.
 * First, the overlay permission cannot be requested with a dialog at all — it
 * is a settings screen the user has to visit, and discovering that mid-task is
 * worse than doing it up front. Second, a missed-call alert is useless if the
 * permission is only requested the first time a call is missed, which is
 * exactly when the app is not open.
 *
 * Every item explains why it is wanted. Skipping is allowed: the app still
 * works, with fewer capabilities, and Access shows what is missing.
 */
class OnboardingActivity : AppCompatActivity() {

    private data class Step(
        val label: String,
        val why: String,
        val permissions: List<String>,
        /** True for the overlay, which has no runtime dialog. */
        val viaSettings: Boolean = false
    )

    private var askedOverlay = false

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            render()
            // Runtime prompts are done; the overlay screen is next.
            if (!overlayGranted() && !askedOverlay) openOverlaySettings()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<OrbView>(R.id.onboardOrb).setState(OrbView.State.IDLE)

        findViewById<TextView>(R.id.onboardAction).setOnClickListener { advance() }
        findViewById<TextView>(R.id.onboardSkip).setOnClickListener { finishSetup() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------------- steps ----------------

    private fun steps(): List<Step> {
        val notifications = if (Build.VERSION.SDK_INT >= 33) {
            listOf("android.permission.POST_NOTIFICATIONS")
        } else {
            emptyList()
        }

        return listOf(
            Step(
                getString(R.string.onboard_phone),
                getString(R.string.onboard_phone_why),
                listOf(
                    Manifest.permission.READ_CALL_LOG,
                    "android.permission.READ_PHONE_STATE",
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
                )
            ),
            Step(
                getString(R.string.onboard_notify),
                getString(R.string.onboard_notify_why),
                notifications
            ),
            Step(
                getString(R.string.onboard_mic),
                getString(R.string.onboard_mic_why),
                listOf(Manifest.permission.RECORD_AUDIO)
            ),
            Step(
                getString(R.string.onboard_overlay),
                getString(R.string.onboard_overlay_why),
                emptyList(),
                viaSettings = true
            )
        )
    }

    private fun granted(step: Step): Boolean =
        if (step.viaSettings) overlayGranted()
        else step.permissions.all { Permissions.has(this, it) }

    private fun overlayGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(this) else true

    // ---------------- rendering ----------------

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.onboardList)
        val inflater = LayoutInflater.from(this)
        list.removeAllViews()

        val all = steps()
        all.forEach { step ->
            val row = inflater.inflate(R.layout.item_onboard_step, list, false)

            row.findViewById<TextView>(R.id.stepLabel).setText(step.label)
            row.findViewById<TextView>(R.id.stepWhy).setText(step.why)

            val ok = granted(step)
            val color = ContextCompat.getColor(
                this,
                if (ok) R.color.status_ok else R.color.text_hint
            )

            row.findViewById<View>(R.id.stepDot).setBackgroundColor(color)

            val state = row.findViewById<TextView>(R.id.stepState)
            state.setText(
                getString(if (ok) R.string.status_allowed else R.string.onboard_pending)
            )
            state.setTextColor(color)

            list.addView(row)
        }

        val remaining = all.count { !granted(it) }
        findViewById<TextView>(R.id.onboardAction).setText(
            if (remaining == 0) getString(R.string.onboard_done)
            else getString(R.string.onboard_continue)
        )
    }

    // ---------------- actions ----------------

    private fun advance() {
        val pending = steps().filter { !granted(it) }

        if (pending.isEmpty()) {
            finishSetup()
            return
        }

        // Runtime permissions first, in one batch, then the overlay screen.
        val runtime = pending.filter { !it.viaSettings }.flatMap { it.permissions }
            .filter { !Permissions.has(this, it) }

        if (runtime.isNotEmpty()) {
            requestPermissions.launch(runtime.toTypedArray())
            return
        }

        openOverlaySettings()
    }

    private fun openOverlaySettings() {
        askedOverlay = true
        try {
            startActivity(
                Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            // Some ROMs hide this screen; the app still works without it.
            render()
        }
    }

    /**
     * Turns on the features that should simply work, then hands over to the
     * main UI. Only reached once — [Prefs.onboardingDone] gates it.
     */
    private fun finishSetup() {
        val prefs = Prefs(this)
        prefs.missedCallAlerts = true
        prefs.loudVoice = true
        // Report calls from now on, not the whole call history.
        prefs.lastMissedCallSeen = System.currentTimeMillis()
        prefs.onboardingDone = true

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
