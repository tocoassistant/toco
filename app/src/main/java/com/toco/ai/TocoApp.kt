package com.toco.ai

import android.app.Application
import com.toco.ai.core.CrashHandler
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
    }

    override fun onTerminate() {
        Voice.shutdown()
        super.onTerminate()
    }
}
