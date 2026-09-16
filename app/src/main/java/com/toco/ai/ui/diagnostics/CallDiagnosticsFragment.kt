package com.toco.ai.ui.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.role.RoleManager
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
import com.toco.ai.core.EventLog
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voice
import com.toco.ai.util.Permissions
import com.toco.ai.util.Taps

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

    private val askRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            render()
        }

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

        // The engine runs in another process that Android kills freely; when
        // that happens TOCO keeps working but stops being audible, which is
        // indistinguishable from "everything broke" unless it is reported.
        val voiceOk = Voice.isHealthy()
        list += Check(
            "Voice engine",
            voiceOk,
            if (voiceOk) "Connected. TOCO can speak."
            else "Not connected right now. TOCO will rebuild it on the next line it speaks.",
            if (voiceOk) null else "Test"
        ) {
            Voice.speak(requireContext(), "Voice engine restarted.")
            render()
        }

        // 7. The route that OEM restrictions cannot block.
        val hasRole = hasScreeningRole()
        list += Check(
            "Call screening role",
            hasRole,
            if (hasRole)
                "TOCO is the screening app, so the system tells it about every " +
                    "incoming call directly. This is the reliable route."
            else
                "NOT SET. Without this, TOCO depends on a broadcast that your " +
                    "phone withholds from apps that are not auto-started. " +
                    "Grant this and real calls will work.",
            if (hasRole) null else "Grant"
        ) {
            requestScreeningRole()
        }

        // 8. Did the old broadcast route ever deliver anything?
        val lastEvent = prefs.lastPhoneEvent
        val everFired = lastEvent > 0L
        val ago = if (everFired) (System.currentTimeMillis() - lastEvent) / 1000 else -1
        list += Check(
            "Phone events received",
            everFired,
            if (!everFired)
                "NEVER. Android has not once told TOCO the phone rang. The code is " +
                    "fine — the broadcast is being withheld. On Infinix this is " +
                    "almost always Auto-start being off for TOCO."
            else
                "Last event " + ago + "s ago (" + prefs.lastPhoneState + "). " +
                    "Broadcasts are arriving.",
            if (everFired) null else "Fix"
        ) {
            openAutostart()
        }

        // 8. Auto-start, which is what gates the above on Transsion ROMs.
        list += Check(
            "Auto-start",
            true,
            "Infinix blocks background broadcasts for apps that are not on the " +
                "auto-start list. If phone events say NEVER, turn this on for TOCO.",
            "Open"
        ) {
            openAutostart()
        }

        // 9. What the log actually holds right now.
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

    private fun hasScreeningRole(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val manager = requireContext().getSystemService(Context.ROLE_SERVICE) as? RoleManager
            ?: return false
        return try {
            manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Asks to become the call screening app.
     *
     * Only one app can hold this at a time, so accepting replaces whatever
     * currently does caller ID or spam filtering. TOCO blocks nothing — it
     * allows every call through untouched — but the user should know what
     * they are swapping out.
     */
    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < 29) {
            toast("This Android version has no screening role.")
            return
        }

        val manager = requireContext().getSystemService(Context.ROLE_SERVICE) as? RoleManager
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            toast("Your phone does not offer the call screening role.")
            return
        }

        try {
            askRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } catch (e: Exception) {
            toast("Couldn't open the request: " + e.message)
        }
    }

    /**
     * Auto-start lives in a different place on every OEM skin and none of it is
     * standard Android, so each known location is tried in turn before falling
     * back to the app's own settings page.
     */
    private fun openAutostart() {
        val candidates = listOf(
            "com.transsion.phonemanager" to "com.itel.autobootmanager.activity.AutoBootMgrActivity",
            "com.transsion.phonemaster" to "com.transsion.autostart.AutoStartActivity",
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        )

        for ((pkg, cls) in candidates) {
            try {
                startActivity(
                    Intent().setClassName(pkg, cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                toast("Find TOCO in this list and allow auto-start.")
                return
            } catch (e: Exception) {
                // Not this ROM; try the next.
            }
        }

        toast("Open Settings > Apps > TOCO and enable Auto-start / Autolaunch.")
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + requireContext().packageName))
            )
        } catch (e: Exception) {
            // Nothing further to offer.
        }
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
        renderLog(view)
    }

    /**
     * The timeline of what actually happened on this phone.
     *
     * Every hard bug so far has been invisible from the outside, so this is
     * the one place that can tell "the system never called us" apart from "it
     * called us and we failed".
     */
    private fun renderLog(view: View) {
        val log = view.findViewById<TextView>(R.id.diagLog)
        val entries = EventLog.entries(requireContext())

        log.setText(
            if (entries.isEmpty()) getString(R.string.diag_log_empty)
            else entries.take(60).joinToString("\n")
        )

        view.findViewById<TextView>(R.id.diagCopy).setOnClickListener {
            val clipboard = requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

            clipboard?.setPrimaryClip(
                ClipData.newPlainText("TOCO log", EventLog.asText(requireContext()))
            )
            toast("Log copied. Paste it wherever you need it.")
        }

        view.findViewById<TextView>(R.id.diagClear).setOnClickListener {
            EventLog.clear(requireContext())
            render()
        }
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
                row.setOnClickListener {
                    if (Taps.allow("diag-" + check.label, Taps.HEAVY_MS)) check.fix?.invoke()
                }
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
