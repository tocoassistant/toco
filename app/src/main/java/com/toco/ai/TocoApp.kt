package com.toco.ai

import android.app.Application
import com.toco.ai.core.CrashHandler
import com.toco.ai.call.CallWatcherService
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.core.Voices
import com.toco.ai.skill.SkillRegistry

class TocoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        SkillRegistry.bootstrap()
        Voice.init(this)
        Voices.applySaved(this)

        // Resume the watcher if the user had it on; services do not survive
        // being killed, so this is where it comes back.
        if (Prefs(this).missedCallAlerts) {
            CallWatcherService.start(this)
        }
    }

    override fun onTerminate() {
        Voice.shutdown()
        super.onTerminate()
    }
}
