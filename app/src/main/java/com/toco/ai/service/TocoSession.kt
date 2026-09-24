package com.toco.ai.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/**
 * The assist session itself.
 *
 * Deliberately invisible. Rather than drawing a panel over whatever the user
 * is doing, it hands off to WakeWordService — which already knows how to
 * listen, route a command to a module, or send a question to Gemini — and then
 * closes. One listening path, not two.
 */
class TocoSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        context.startActivity(
            Intent(context, com.toco.ai.ui.listening.ListeningActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        hide()
    }
}
