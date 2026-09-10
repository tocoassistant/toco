package com.toco.ai.ui.assist

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.toco.ai.service.WakeWordService
import com.toco.ai.util.Permissions

/**
 * The screen Android opens when TOCO is the device assistant.
 *
 * This is what makes long-pressing home (or the power button, or the assist
 * gesture) start TOCO listening — the closest Android equivalent to holding
 * the side button for Siri.
 *
 * It has no UI of its own on purpose: it starts the listening session and
 * finishes immediately, so the user sees their current screen with TOCO
 * listening over it rather than being yanked into an app.
 */
class AssistActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Permissions.has(this, Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "TOCO needs microphone access first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ACTION_TALK skips the wake word — the gesture IS the wake word.
        startService(
            android.content.Intent(this, WakeWordService::class.java)
                .setAction(WakeWordService.ACTION_TALK)
        )

        finish()
    }
}
