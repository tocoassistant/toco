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
import com.toco.ai.core.Prefs
import com.toco.ai.util.Permissions
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
    private var calls: List<MissedCall> = emptyList()

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    fun canShow(): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true

    fun show(missed: List<MissedCall>) {
        if (missed.isEmpty() || !canShow()) return

        dismiss()
        calls = grouped(missed)
        expanded = false

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_missed, null, false)
        root = view

        view.findViewById<TextView>(R.id.overlayClose).setOnClickListener { dismiss() }

        render()

        try {
            windowManager?.addView(view, params())
        } catch (e: Exception) {
            // Permission revoked between the check and the add.
            root = null
            return
        }

        // Slide-and-fade in, so it doesn't just snap into existence.
        view.alpha = 0f
        view.translationY = -40f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260)
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
    }

    // ---------------- content ----------------

    /**
     * Several calls from one number become one row with a count, which is what
     * the user cares about — "Alex, 3 calls", not three identical rows.
     */
    private fun grouped(missed: List<MissedCall>): List<MissedCall> =
        missed.groupBy { it.number.ifBlank { it.name ?: "unknown" } }
            .map { (_, group) -> group.maxByOrNull { it.time }!! to group.size }
            .sortedByDescending { it.first.time }
            .map { it.first }

    private fun countFor(call: MissedCall): Int =
        calls.count { it.number == call.number }.coerceAtLeast(1)

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
            more.setOnClickListener { expand() }
        } else {
            more.visibility = View.GONE
        }

        handle.visibility = if (expanded) View.VISIBLE else View.GONE

        // Ignore All is useful whether the list is collapsed or expanded, so
        // unlike the old Call All it is always offered.
        ignoreButton.visibility = View.VISIBLE
        ignoreButton.setOnClickListener { ignoreAll() }
    }

    private fun rawCount(): Int = calls.sumOf { countFor(it) }

    private fun row(inflater: LayoutInflater, parent: LinearLayout, call: MissedCall): View {
        val view = inflater.inflate(R.layout.item_overlay_caller, parent, false)

        val label = call.label()
        view.findViewById<TextView>(R.id.callerName).setText(label)
        view.findViewById<TextView>(R.id.callerInitial)
            .setText(label.trim().take(1).uppercase())

        val count = countFor(call)
        view.findViewById<TextView>(R.id.callerMeta).setText(
            if (count == 1) context.getString(R.string.overlay_one_call)
            else context.getString(R.string.overlay_n_calls, count)
        )

        view.findViewById<TextView>(R.id.callerTime).setText(clock(call.time))

        val dial = { _: View -> callBack(call) }
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
            // NOT_FOCUSABLE keeps the keyboard and the app underneath working;
            // without it the overlay would swallow every key press.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 60
        }
    }

    private fun clock(time: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))

    private companion object {
        const val COLLAPSED_ROWS = 2
    }
}
