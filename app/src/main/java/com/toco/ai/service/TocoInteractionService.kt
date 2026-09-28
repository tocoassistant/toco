package com.toco.ai.service

import android.Manifest
import android.service.voice.VoiceInteractionService
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs
import com.toco.ai.util.Permissions

/** System digital-assistant entry point and a reliable owner for TOCO's wake preference. */
class TocoInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        val prefs = Prefs(this)
        if (prefs.wakeEnabled && Permissions.has(this, Manifest.permission.RECORD_AUDIO)) {
            try {
                WakeWordService.start(this)
                EventLog.log(this, "ASSISTANT", "wake listener restored")
            } catch (e: Exception) {
                EventLog.log(this, "ASSISTANT", "wake restore refused: " + e.message)
            }
        }
    }
}
