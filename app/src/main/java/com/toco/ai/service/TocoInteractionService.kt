package com.toco.ai.service

import android.service.voice.VoiceInteractionService

/**
 * Registers TOCO as a candidate for the system Digital Assistant role.
 *
 * Android only lists apps that provide a VoiceInteractionService in
 * Settings -> Default apps -> Digital assistant app. Without this class TOCO
 * cannot be chosen there at all, no matter what intents it handles.
 */
class TocoInteractionService : VoiceInteractionService()
