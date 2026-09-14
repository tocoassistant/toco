package com.toco.ai.ui.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.toco.ai.R
import com.toco.ai.call.MissedCall
import com.toco.ai.call.MissedCallNotifier
import com.toco.ai.call.MissedCallOverlay
import com.toco.ai.call.MissedCallReader
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.util.Permissions

/**
 * Answers "why isn't this working" without another round trip.
 *
 * Every call feature depends on a chain: permission -> broadcast -> service ->
 * notification/overlay/speech. A break anywhere looks identical from the
 * outside — nothing happens — which is exactly the failure mode that wastes
 * the most time. This checks each link and says which one is broken, then
 * offers the fix for it.
 *
 * The test buttons drive the real code paths, not imitations, so a passing
 * test means the feature works when a real call arrives.
 */
class CallDiagnosticsFragment : Fragment() {

    private data class Check(
        val label: String,
        val ok: Boolean,
        val detail: String,
        val fixLabel: String? = null,
        val fix: (() -> Unit)? = null
    )

    private lateinit var prefs: Prefs
    private var overlay: MissedCallOverlay? = null

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            render()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_diagnostics, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroyView() {
        overlay?.dismiss()
        overlay = null
        super.onDestroyView()
    }

    // ---------------- checks ----------------

    private fun checks(): List<Check> {
        val context = requireContext()
        val list = mutableListOf<Check>()

        // 1. The permission that gates the PHONE_STATE broadcast entirely.
        // Without it Android never delivers the broadcast, so nothing else
        // in the chain can run — no announcement, no notification, no overlay.
        val phoneState = Permissions.has(context, "android.permission.READ_PHONE_STATE")
        list += Check(
            "Phone status permission",
            phoneState,
            if (phoneState) "Granted. TOCO is told when the phone rings."
            else "MISSING. Android will not tell TOCO the phone is ringing, so nothing works.",
            if (phoneState) null else "Grant"
        ) {
            askPermissions.launch(arrayOf("android.permission.READ_PHONE_STATE"))
        }

        val callLog = Permissions.has(context, Manifest.permission.READ_CALL_LOG)
        list += Check(
            "Call log permission",
            callLog,
            if (callLog) "Granted. TOCO can see who called."
            else "MISSING. TOCO cannot read missed calls.",
            if (callLog) null else "Grant"
        ) {
            askPermissions.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
        }

        val contacts = Permissions.has(context, Manifest.permission.READ_CONTACTS)
        list += Check(
            "Contacts permission",
            contacts,
            if (contacts) "Granted. Callers are named."
            else "Missing. Callers will be read out as numbers.",
            if (contacts) null else "Grant"
        ) {
            askPermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }

        // 2. Notifications can be off at the system level even when the
        // runtime permission was granted.
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager
        val notifOn = manager?.areNotificationsEnabled() ?: false
        list += Check(
            "Notifications enabled",
            notifOn,
            if (notifOn) "On. Missed call alerts can appear."
            else "OFF for TOCO in system settings. No alert will ever show.",
            if (notifOn) null else "Open"
        ) {
            startActivity(
                Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
            )
        }

        // 3. The overlay is refused outright without this.
        val canOverlay =
            if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true
        list += Check(
            "Display over other apps",
            canOverlay,
            if (canOverlay) "Granted. The missed call card can show."
            else "MISSING. You will get the notification but no card.",
            if (canOverlay) null else "Open"
        ) {
            startActivity(
                Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
                    .setData(Uri.parse("package:" + context.packageName))
            )
        }

        // 4. OEM battery managers kill the watcher before it can announce.
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val unrestricted = if (Build.VERSION.SDK_INT >= 23) {
            power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
        list += Check(
            "Battery unrestricted",
            unrestricted,
            if (unrestricted) "TOCO will not be killed before it can speak."
            else "Restricted. Your phone may kill TOCO before the alert plays.",
            if (unrestricted) null else "Open"
        ) {
            startActivity(Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"))
        }

        // 5. Ring volume zero means the announcement is silent.
        val ring = Voice.ringVolumePercent(context)
        list += Check(
            "Ring volume",
            ring != 0,
            if (ring < 0) "Could not read."
            else if (ring == 0) "Silent. TOCO will show alerts but you will hear nothing."
            else "$ring%. TOCO will be audible."
        )

        // 6. The baseline that broke everything before.
        val since = prefs.lastMissedCallSeen
        val age = (System.currentTimeMillis() - since) / 1000
        list += Check(
            "Missed call baseline",
            age >= 0,
            "Reporting calls newer than " + age + "s ago. This value must stay " +
                "put between checks; if it keeps resetting, nothing is ever new."
        )

        // 7. What the log actually holds right now.
        val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val recent = if (callLog) MissedCallReader.since(context, dayAgo) else emptyList()
        val pending = if (callLog) MissedCallReader.since(context, since) else emptyList()
        list += Check(
            "Missed calls found",
            true,
            recent.size.toString() + " in the last 24h, " + pending.size + " not yet announced."
        )

        return list
    }

    // ---------------- tests ----------------

    private fun tests(): List<Check> = listOf(
        Check(
            "Test voice",
            true,
            "Speaks a line at the volume a real alert would use.",
            "Run"
        ) {
            Voice.speak(requireContext(), "This is how a missed call alert sounds.")
        },
        Check(
            "Test notification",
            true,
            "Posts a real missed call notification using the last 24 hours.",
            "Run"
        ) { testNotification() },
        Check(
            "Test card",
            true,
            "Shows the missed call card exactly as it appears after unlocking.",
            "Run"
        ) { testOverlay() }
    )

    private fun sample(): List<MissedCall> {
        val real = MissedCallReader.since(
            requireContext(),
            System.currentTimeMillis() - 24L * 60 * 60 * 1000
        )
        if (real.isNotEmpty()) return real

        // Nothing in the log; use stand-ins so the UI can still be checked.
        val now = System.currentTimeMillis()
        return listOf(
            MissedCall("+8801327110515", "Ma", now - 60_000),
            MissedCall("+8801727580392", "Mama", now - 240_000),
            MissedCall("+8801798131260", null, now - 900_000)
        )
    }

    private fun testNotification() {
        MissedCallNotifier.post(requireContext(), sample())
        toast("Posted. Check your notification shade.")
    }

    private fun testOverlay() {
        val context = requireContext()
        val card = MissedCallOverlay(context)

        if (!card.canShow()) {
            toast("Blocked: Display over other apps is off.")
            startActivity(
                Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
                    .setData(Uri.parse("package:" + context.packageName))
            )
            return
        }

        overlay?.dismiss()
        overlay = card
        card.show(sample())
    }

    // ---------------- rendering ----------------

    private fun render() {
        if (!isAdded) return
        val view = view ?: return

        val all = checks()
        val failing = all.count { !it.ok }

        view.findViewById<TextView>(R.id.diagSummary).setText(
            if (failing == 0) getString(R.string.diag_all_good)
            else getString(R.string.diag_problems, failing)
        )

        fill(view.findViewById(R.id.diagList), all)
        fill(view.findViewById(R.id.diagTests), tests())
    }

    private fun fill(parent: LinearLayout, items: List<Check>) {
        val inflater = LayoutInflater.from(requireContext())
        parent.removeAllViews()

        items.forEach { check ->
            val row = inflater.inflate(R.layout.item_diag, parent, false)

            row.findViewById<TextView>(R.id.diagLabel).setText(check.label)
            row.findViewById<TextView>(R.id.diagDetail).setText(check.detail)

            val color = ContextCompat.getColor(
                requireContext(),
                if (check.ok) R.color.status_ok else R.color.status_warn
            )
            row.findViewById<View>(R.id.diagDot).setBackgroundColor(color)

            val action = row.findViewById<TextView>(R.id.diagAction)
            if (check.fixLabel != null) {
                action.setText(check.fixLabel)
                row.setOnClickListener { check.fix?.invoke() }
            } else {
                action.setText("")
                row.isClickable = false
            }

            parent.addView(row)
        }
    }

    private fun toast(message: String) {
        if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}
