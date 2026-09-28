package com.toco.ai.ui.profile

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.toco.ai.R
import com.toco.ai.core.EventLog
import com.toco.ai.core.ConversationStore
import com.toco.ai.core.Prefs
import com.toco.ai.core.AuthManager
import com.toco.ai.ui.auth.LoginActivity
import android.content.Intent
import com.toco.ai.core.TaskStore
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val prefs = Prefs(this)
        findViewById<TextView>(R.id.profileName).text = prefs.userName
        findViewById<TextView>(R.id.profileAvatar).text = prefs.userName.firstOrNull()?.uppercase() ?: "T"
        val auth = AuthManager(this)
        findViewById<TextView>(R.id.profileAccount).text = auth.accountLabel
        findViewById<android.view.View>(R.id.profileSignOut).setOnClickListener {
            auth.signOut {
                startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }
        findViewById<android.view.View>(R.id.profileBack).setOnClickListener { finish() }
        val entries = EventLog.entries(this)
        findViewById<TextView>(R.id.activityContent).text = if (entries.isEmpty()) getString(R.string.activity_empty) else entries.take(80).joinToString("\n\n")
        // History backend is intentionally represented separately from the device action log.
        // Quick commands never appear here; Supabase-backed meaningful sessions plug into this panel.
        findViewById<TextView>(R.id.historyContent).text = ConversationStore.summary(this)
        renderTasks()
    }
    private fun renderTasks() {
        val list = findViewById<LinearLayout>(R.id.taskList)
        list.removeAllViews()
        val tasks = TaskStore.tasks(this)
        if (tasks.isEmpty()) {
            TextView(this).also { it.text = "No tasks yet. Say ‘remember to…’ to TOCO."; it.setTextColor(getColor(R.color.text_secondary)); it.setPadding(8,16,8,16); list.addView(it) }
            return
        }
        tasks.take(30).forEach { task ->
            val row = layoutInflater.inflate(R.layout.item_task_card, list, false)
            row.findViewById<TextView>(R.id.taskState).text = task.state
            row.findViewById<TextView>(R.id.taskText).text = task.text
            row.findViewById<TextView>(R.id.taskTime).text = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(task.createdAt))
            val actions = row.findViewById<View>(R.id.taskActions)
            actions.visibility = if (task.state == "Pending") View.VISIBLE else View.GONE
            row.findViewById<View>(R.id.taskDone).setOnClickListener { TaskStore.complete(this, task.id); renderTasks() }
            row.findViewById<View>(R.id.taskCancel).setOnClickListener { TaskStore.cancel(this, task.id); renderTasks() }
            list.addView(row)
        }
    }
}
