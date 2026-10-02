package com.toco.ai.island

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.toco.ai.R
import com.toco.ai.call.OverlayPermissionNotice
import com.toco.ai.core.Prefs
import com.toco.ai.ui.MainActivity
import com.toco.ai.ui.widget.OrbView

/**
 * Persistent TOCO Island overlay. It is intentionally separate from wake-word
 * and voice-interaction services so enabling the Island cannot disturb them.
 */
class IslandService : Service() {

    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var soundEngine: SoundEngine? = null
    private val windowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!canDraw()) {
            Prefs(this).islandEnabled = false
            OverlayPermissionNotice.post(this)
            stopSelf()
            return
        }

        soundEngine = SoundEngine(this)
        showIsland()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).islandEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        soundEngine?.release()
        soundEngine = null
        super.onDestroy()
    }

    private fun showIsland() {
        if (root != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_toco_island, null)
        val orb = view.findViewById<OrbView>(R.id.islandOrb)
        val compactLabel = view.findViewById<TextView>(R.id.islandCompactLabel)
        val expandedContent = view.findViewById<LinearLayout>(R.id.islandExpandedContent)
        val close = view.findViewById<TextView>(R.id.islandClose)

        orb.setMood(OrbView.Mood.IDLE)
        compactLabel.setOnClickListener { setExpanded(!expanded, view, expandedContent, orb) }
        orb.setOnClickListener { setExpanded(!expanded, view, expandedContent, orb) }
        close.setOnClickListener { setExpanded(false, view, expandedContent, orb) }

        val lp = WindowManager.LayoutParams(
            dp(COMPACT_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(10)
        }

        params = lp
        root = view
        windowManager.addView(view, lp)
        view.alpha = 0f
        view.translationY = -dp(18).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setExpanded(
        target: Boolean,
        view: View,
        expandedContent: View,
        orb: OrbView
    ) {
        if (expanded == target) return
        expanded = target

        if (target) {
            expandedContent.visibility = View.VISIBLE
            expandedContent.alpha = 0f
            expandedContent.translationY = -dp(6).toFloat()
            expandedContent.animate().alpha(1f).translationY(0f).setDuration(180L).start()
            orb.setMood(OrbView.Mood.PEEK)
            soundEngine?.play(OrbView.Mood.PEEK)
        } else {
            orb.setMood(OrbView.Mood.IDLE)
            expandedContent.animate().alpha(0f).translationY(-dp(6).toFloat()).setDuration(120L)
                .withEndAction { expandedContent.visibility = View.GONE }
                .start()
        }

        val lp = params ?: return
        val start = lp.width
        val end = dp(if (target) EXPANDED_WIDTH_DP else COMPACT_WIDTH_DP)
        ValueAnimator.ofInt(start, end).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                lp.width = it.animatedValue as Int
                if (root === view) runCatching { windowManager.updateViewLayout(view, lp) }
            }
            start()
        }
    }

    private fun canDraw(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            71,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            72,
            Intent(this, IslandService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.island_notification_title))
            .setContentText(getString(R.string.island_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.island_stop), stop).build())
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.island_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "toco_island"
        private const val NOTIFICATION_ID = 71
        private const val ACTION_STOP = "com.toco.ai.island.STOP"
        private const val COMPACT_WIDTH_DP = 154
        private const val EXPANDED_WIDTH_DP = 340

        fun start(context: Context) {
            val intent = Intent(context, IslandService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandService::class.java))
        }
    }
}
