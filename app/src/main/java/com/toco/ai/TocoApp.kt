package com.toco.ai

import android.app.Application
import com.toco.ai.core.CrashHandler
import com.toco.ai.call.IncomingCallAnnouncer
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

        // If a previous run was killed while the ringtone was ducked, nothing
        // else will ever put it back. Do it now.
        IncomingCallAnnouncer.restoreVolumeIfStranded(this)
    }

    override fun onTerminate() {
        Voice.shutdown()
        super.onTerminate()
    }
}
