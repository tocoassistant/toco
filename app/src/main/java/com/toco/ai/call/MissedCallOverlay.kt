package com.toco.ai.call

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.toco.ai.R
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs
import com.toco.ai.util.Permissions
import com.toco.ai.util.Taps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The floating card that appears over the home screen after unlocking.
 *
 * Drawn with WindowManager rather than an Activity on purpose: an Activity
 * would take over the screen and pull the user out of whatever they were
 * doing. This sits on top, and the phone underneath stays usable.
 *
 * Needs "Display over other apps" (SYSTEM_ALERT_WINDOW). Without it Android
 * refuses the window outright, so [canShow] is checked before every attempt
 * and the notification remains the fallback.
 *
 * Collapsed it shows two callers plus a "+N more" button; expanded it shows
 * everyone with a Call button each.
 */
class MissedCallOverlay(private val context: Context) {

    private var root: View? = null
    private var expanded = false

    /** Told when the card goes away, so its owner can stop itself. */
    var onClosed: (() -> Unit)? = null
    private var calls: List<MissedCall> = emptyList()

    /** How many times each caller rang, keyed the same way as [grouped]. */
    private var counts: Map<String, Int> = emptyMap()

    /** Total calls before grouping, which is what the title reports. */
    private var totalCalls = 0

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    fun canShow(): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true

    fun show(missed: List<MissedCall>) {
        if (missed.isEmpty()) return

        if (!canShow()) {
            EventLog.log(context, "OVERLAY", "refused: no Display-over-other-apps permission")
            OverlayPermissionNotice.post(context)
            return
        }

        dismiss()
        calls = grouped(missed)
        expanded = false

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_missed, null, false)
        root = view

        view.findViewById<TextView>(R.id.overlayClose).setOnClickListener {
            if (Taps.allow("overlay-close")) dismiss()
        }

        render()

        try {
            windowManager?.addView(view, params())
            EventLog.log(context, "OVERLAY", "shown with " + calls.size + " caller(s)")
        } catch (e: Exception) {
            // The single most useful line in the whole log: the window manager
            // refusing the view is invisible by definition.
            EventLog.log(context, "OVERLAY", "addView FAILED: " + e.javaClass.simpleName + " " + e.message)
            root = null
            return
        }

        // Rises from the bottom edge, which reads as a sheet being pulled up
        // rather than a notification dropping in.
        view.alpha = 0f
        view.translationY = 220f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            // Already gone.
        }
        onClosed?.invoke()
    }

    /** True while the card is on screen. */
    fun isShowing(): Boolean = root != null

    // ---------------- content ----------------

    /**
     * Several calls from one number become one row with a count, which is what
     * the user cares about — "Alex, 3 calls", not three identical rows.
     */
    private fun grouped(missed: List<MissedCall>): List<MissedCall> {
        val byCaller = missed.groupBy { key(it) }

        // Keep the counts. The previous version computed them and discarded
        // them, then recounted against the already-deduplicated list, so every
        // row reported "1 call" no matter how many times someone rang.
        counts = byCaller.mapValues { (_, group) -> group.size }
        totalCalls = missed.size

        return byCaller
            .map { (_, group) -> group.maxByOrNull { it.time }!! }
            .sortedByDescending { it.time }
    }

    /** Blank numbers group under the name so unknown callers don't all merge. */
    private fun key(call: MissedCall): String =
        call.number.ifBlank { call.name ?: "unknown" }

    private fun countFor(call: MissedCall): Int =
        counts[key(call)] ?: 1

    private fun render() {
        val view = root ?: return

        val list = view.findViewById<LinearLayout>(R.id.overlayList)
        val title = view.findViewById<TextView>(R.id.overlayTitle)
        val more = view.findViewById<TextView>(R.id.overlayMore)
        val ignoreButton = view.findViewById<TextView>(R.id.overlayIgnoreAll)
        val handle = view.findViewById<View>(R.id.overlayHandle)

        val total = rawCount()
        title.setText(
            if (total == 1) context.getString(R.string.overlay_one)
            else context.getString(R.string.overlay_many, total)
        )

        val visible = if (expanded) calls else calls.take(COLLAPSED_ROWS)
        val hidden = calls.size - visible.size

        list.removeAllViews()
        val inflater = LayoutInflater.from(context)
        visible.forEach { call -> list.addView(row(inflater, list, call)) }

        if (hidden > 0) {
            more.visibility = View.VISIBLE
            more.setText(context.getString(R.string.overlay_more, hidden))
            more.setOnClickListener { if (Taps.allow("overlay-expand")) expand() }
        } else {
            more.visibility = View.GONE
        }

        handle.visibility = if (expanded) View.VISIBLE else View.GONE

        // Ignore All is useful whether the list is collapsed or expanded, so
        // unlike the old Call All it is always offered.
        ignoreButton.visibility = View.VISIBLE
        ignoreButton.setOnClickListener { if (Taps.allow("overlay-ignore")) ignoreAll() }
    }

    private fun rawCount(): Int = totalCalls

    private fun row(inflater: LayoutInflater, parent: LinearLayout, call: MissedCall): View {
        val view = inflater.inflate(R.layout.item_overlay_caller, parent, false)

        val label = call.label()
        view.findViewById<TextView>(R.id.callerName).setText(label)
        view.findViewById<TextView>(R.id.callerInitial)
            .setText(label.trim().take(1).uppercase())

        // One line that answers both questions at a glance: how many times,
        // and how long ago. Two separate labels made the row read like a table.
        val count = countFor(call)
        val times = if (count == 1) {
            context.getString(R.string.overlay_one_call)
        } else {
            context.getString(R.string.overlay_n_calls, count)
        }
        view.findViewById<TextView>(R.id.callerMeta).setText(times + "  \u00b7  " + ago(call.time))

        // One key per caller, so tapping two different people quickly still
        // works while hammering one of them does not dial repeatedly.
        val dial = { _: View ->
            if (Taps.allow("overlay-call-" + call.number, Taps.HEAVY_MS)) callBack(call)
        }
        view.setOnClickListener(dial)
        view.findViewById<TextView>(R.id.callerCall).setOnClickListener(dial)

        return view
    }

    private fun expand() {
        expanded = true
        render()

        // Grow from the collapsed height rather than jumping to full size.
        root?.let { view ->
            view.translationY = 24f
            view.animate()
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ---------------- actions ----------------

    private fun callBack(call: MissedCall) {
        if (call.number.isBlank()) return
        dismiss()

        val direct = Permissions.has(context, android.Manifest.permission.CALL_PHONE)
        val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL

        try {
            context.startActivity(
                Intent(action, Uri.parse("tel:" + call.number))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Nothing sensible to fall back to; the overlay is already gone.
        }
    }

    /**
     * Clears the lot: closes the card, cancels the notification, and marks
     * everything as seen so these calls are never announced again.
     *
     * This replaced a "Call All" button. Android permits one call at a time,
     * so that button could only ever ring the first person — which is not what
     * the words promised. Dismissing a stack of missed calls in one tap is the
     * thing people actually want.
     */
    private fun ignoreAll() {
        val newest = calls.maxOfOrNull { it.time }
        if (newest != null) {
            Prefs(context).lastMissedCallSeen = newest
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager
        manager?.cancel(MissedCallNotifier.ALERT_ID)

        dismiss()
    }

    // ---------------- window ----------------

    private fun params(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_FOCUSABLE keeps the app underneath usable; DIM_BEHIND is what
            // darkens everything else while the card is up.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0

            // 0.75 as asked. Real background blur only exists on Android 12+
            // and is applied there as well; on older versions the dim alone is
            // what separates the card from the screen behind it.
            dimAmount = DIM

            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    blurBehindRadius = BLUR_RADIUS
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                } catch (e: Throwable) {
                    // Device or ROM without blur support; dim still applies.
                }
            }
        }
    }

    /**
     * "Just now", "12 min ago", "2 hr ago", then a clock time once the day has
     * moved on. Relative wording is easier to act on than a bare timestamp.
     */
    private fun ago(time: Long): String {
        val minutes = (System.currentTimeMillis() - time) / 60000

        return when {
            minutes < 1 -> context.getString(R.string.ago_now)
            minutes < 60 -> context.getString(R.string.ago_min, minutes)
            minutes < 24 * 60 -> context.getString(R.string.ago_hour, minutes / 60)
            else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(time))
        }
    }

    private companion object {
        const val COLLAPSED_ROWS = 2

        /** How dark everything behind the card goes. */
        const val DIM = 0.75f

        const val BLUR_RADIUS = 40
    }
}
