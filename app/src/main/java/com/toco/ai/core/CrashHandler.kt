package com.toco.ai.core

import android.content.Context
import android.content.Intent
import com.toco.ai.ui.debug.CrashActivity

/**
 * Routes uncaught exceptions to a readable on-screen stack trace.
 * Invaluable when building on-device, where there's no logcat window.
 */
object CrashHandler {

    fun install(context: Context) {
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val intent = Intent(app, CrashActivity::class.java)
                    .putExtra(CrashActivity.EXTRA_TRACE, throwable.stackTraceToString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
            } catch (ignored: Throwable) {
                // Nothing left to do — fall through to process kill.
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
