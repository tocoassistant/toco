package com.toco.ai.ui.profile

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.toco.ai.R
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val prefs = Prefs(this)
        findViewById<TextView>(R.id.profileName).text = prefs.userName
        findViewById<TextView>(R.id.profileAvatar).text = prefs.userName.firstOrNull()?.uppercase() ?: "T"
        findViewById<android.view.View>(R.id.profileBack).setOnClickListener { finish() }
        val entries = EventLog.entries(this)
        findViewById<TextView>(R.id.activityContent).text = if (entries.isEmpty()) getString(R.string.activity_empty) else entries.take(80).joinToString("\n\n")
        // History backend is intentionally represented separately from the device action log.
        // Quick commands never appear here; Supabase-backed meaningful sessions plug into this panel.
        findViewById<TextView>(R.id.historyContent).setText(R.string.history_empty)
    }
}
