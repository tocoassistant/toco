package com.toco.ai.access

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.toco.ai.util.Permissions

/**
 * Single source of truth for what TOCO is actually allowed to do.
 *
 * Everything here reads live system state. Nothing is cached and nothing is
 * assumed, because a user can revoke a permission from settings at any moment
 * and a stale "Allowed" badge is worse than no badge.
 */
object PermissionManager {

    // Capability IDs referenced from the UI.
    const val ID_ACCESSIBILITY = "accessibility"
    const val ID_NOTIFICATION_ACCESS = "notification_access"
    const val ID_OVERLAY = "overlay"

    /**
     * Declared capabilities. Nothing is listed here that TOCO does not have a
     * real use for, and each carries the explanation shown to the user.
     */
    fun capabilities(): List<Capability> = listOf(
        Capability(
            id = "microphone",
            label = "Microphone",
            explanation = "Lets you speak commands instead of typing them.",
            gate = Gate.RUNTIME,
            permissions = listOf(Manifest.permission.RECORD_AUDIO)
        ),
        Capability(
            id = ID_ACCESSIBILITY,
            label = "Accessibility",
            explanation = "Required for Back, Home, scrolling and tapping inside other apps. " +
                "Android only allows you to turn this on yourself, in system settings.",
            gate = Gate.SYSTEM_SETTINGS
        ),
        Capability(
            id = ID_NOTIFICATION_ACCESS,
            label = "Notification access",
            explanation = "Lets TOCO read and dismiss notifications when you ask. " +
                "Granted in system settings, not by TOCO.",
            gate = Gate.SYSTEM_SETTINGS
        ),
        Capability(
            id = "notifications",
            label = "Show notifications",
            explanation = "Lets TOCO show you the result of a long-running action.",
            gate = Gate.RUNTIME,
            permissions = if (Build.VERSION.SDK_INT >= 33) {
                listOf("android.permission.POST_NOTIFICATIONS")
            } else {
                emptyList()
            }
        ),
        Capability(
            id = "phone",
            label = "Phone",
            explanation = "Lets TOCO place a call when you ask it to.",
            gate = Gate.RUNTIME,
            permissions = listOf(Manifest.permission.CALL_PHONE)
        ),
        Capability(
            id = "camera",
            label = "Camera",
            explanation = "Needed only if you ask TOCO to open the camera and capture.",
            gate = Gate.RUNTIME,
            permissions = listOf(Manifest.permission.CAMERA)
        ),
        Capability(
            id = "location",
            label = "Location",
            explanation = "Used for location-aware answers. Coarse location is enough.",
            gate = Gate.RUNTIME,
            permissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        ),
        Capability(
            id = "contacts",
            label = "Contacts",
            explanation = "Lets you say a name instead of a phone number.",
            gate = Gate.RUNTIME,
            permissions = listOf(Manifest.permission.READ_CONTACTS)
        ),
        Capability(
            id = "calendar",
            label = "Calendar",
            explanation = "Lets TOCO answer questions about your schedule.",
            gate = Gate.RUNTIME,
            permissions = listOf(Manifest.permission.READ_CALENDAR)
        ),
        Capability(
            id = "bluetooth",
            label = "Bluetooth",
            explanation = "Lets TOCO see connected devices. Android does not let any " +
                "third-party app switch Bluetooth on or off directly.",
            gate = Gate.RUNTIME,
            permissions = if (Build.VERSION.SDK_INT >= 31) {
                listOf("android.permission.BLUETOOTH_CONNECT")
            } else {
                emptyList()
            }
        ),
        Capability(
            id = ID_OVERLAY,
            label = "Display over other apps",
            explanation = "Lets TOCO open apps and dial while the screen is locked. " +
                "Android blocks background apps from starting screens without this.",
            gate = Gate.SYSTEM_SETTINGS
        ),
        Capability(
            id = "flashlight",
            label = "Flashlight",
            explanation = "Works with no permission at all on this Android version.",
            gate = Gate.ALWAYS
        ),
        Capability(
            id = "apps",
            label = "Apps",
            explanation = "TOCO can list and launch apps that appear in your launcher. " +
                "Android hides other installed packages, and cannot force-stop apps.",
            gate = Gate.ALWAYS
        )
    )

    /** Live status for a capability. Never cached. */
    fun status(context: Context, capability: Capability): CapabilityStatus {
        if (capability.minSdk != 0 && Build.VERSION.SDK_INT < capability.minSdk) {
            return CapabilityStatus.UNSUPPORTED
        }

        return when (capability.gate) {
            Gate.ALWAYS -> CapabilityStatus.ALLOWED

            Gate.SYSTEM_SETTINGS -> when (capability.id) {
                ID_ACCESSIBILITY ->
                    if (isAccessibilityEnabled(context)) CapabilityStatus.ALLOWED
                    else CapabilityStatus.NOT_CONNECTED

                ID_NOTIFICATION_ACCESS ->
                    if (isNotificationAccessGranted(context)) CapabilityStatus.ALLOWED
                    else CapabilityStatus.NOT_CONNECTED

                ID_OVERLAY ->
                    if (Settings.canDrawOverlays(context)) CapabilityStatus.ALLOWED
                    else CapabilityStatus.NOT_CONNECTED

                else -> CapabilityStatus.NOT_CONNECTED
            }

            Gate.RUNTIME -> {
                // A capability whose permission does not exist on this API level
                // needs nothing, so it is genuinely allowed.
                if (capability.permissions.isEmpty()) return CapabilityStatus.ALLOWED

                val granted = capability.permissions.count { Permissions.has(context, it) }
                when {
                    granted == capability.permissions.size -> CapabilityStatus.ALLOWED
                    granted > 0 -> CapabilityStatus.LIMITED
                    else -> CapabilityStatus.DENIED
                }
            }
        }
    }

    // ---------------- system-settings state ----------------

    /**
     * Reads the enabled-services list rather than asking AccessibilityManager,
     * because the manager reports services that are running, which is not the
     * same as ours being switched on.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    // ---------------- settings deep links ----------------

    /**
     * The correct settings screen for a capability, or null when TOCO should
     * request it with a runtime dialog instead.
     */
    fun settingsIntent(context: Context, capability: Capability): Intent? =
        when {
            capability.id == ID_ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

            capability.id == ID_NOTIFICATION_ACCESS ->
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

            capability.id == ID_OVERLAY ->
                Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
                    .setData(Uri.parse("package:" + context.packageName))

            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** App info screen — the only route left once a user selects "don't ask again". */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
