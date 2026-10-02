package com.toco.ai

import android.app.Application
import com.toco.ai.core.CrashHandler
import com.toco.ai.call.IncomingCallAnnouncer
import com.toco.ai.call.MissedCallCheck
import com.toco.ai.core.Voice
import com.toco.ai.core.Voices
import com.toco.ai.skill.SkillRegistry
import com.toco.ai.core.Prefs
import com.toco.ai.island.IslandService
import android.os.Build
import android.provider.Settings

class TocoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        SkillRegistry.bootstrap()
        Voice.init(this)
        Voices.applySaved(this)

        // Restore the user-enabled Island when TOCO itself is opened. This is
        // intentionally separate from wake-word and voice-session services.
        if (Prefs(this).islandEnabled &&
            (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))) {
            IslandService.start(this)
        }

        // If a previous run was killed while the ringtone was ducked, nothing
        // else will ever put it back. Do it now.
        IncomingCallAnnouncer.restoreVolumeIfStranded(this)

        // Opening TOCO is itself a moment the user is present, so report
        // anything that came in while it was closed.
        MissedCallCheck.checkNow(this)
    }

    override fun onTerminate() {
        Voice.shutdown()
        super.onTerminate()
    }
}
