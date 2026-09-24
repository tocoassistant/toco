package com.toco.ai.ui.assist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.toco.ai.ui.listening.ListeningActivity

/**
 * The entry point Android calls when TOCO is the device assistant and the user
 * makes the assist gesture (long-press home, power button, etc.).
 *
 * It exists only to forward to ListeningActivity. Being launched as the
 * assistant is what lets that screen appear over whatever the user was doing,
 * including the lock screen.
 */
class AssistActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, ListeningActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        finish()
    }
}
