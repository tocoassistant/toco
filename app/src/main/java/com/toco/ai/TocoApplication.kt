package com.toco.ai

import android.app.Application
import android.content.Intent

class TocoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("crash_message", throwable.stackTraceToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
