package com.toco.ai.ui.access

import android.os.Bundle
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
import com.toco.ai.access.Capability
import com.toco.ai.access.CapabilityStatus
import com.toco.ai.access.Gate
import com.toco.ai.access.PermissionManager

/**
 * TOCO Access.
 *
 * Rebuilds from live system state in onResume, so returning from a settings
 * screen shows the real result instead of a stale badge. Nothing here reports
 * a capability as working when Android would refuse it.
 */
class AccessFragment : Fragment() {

    private lateinit var list: LinearLayout

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { !it }.keys
            if (denied.isNotEmpty() && isAdded) {
                Toast.makeText(
                    requireContext(),
                    "Not granted. You can still allow it in system settings.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            render()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_access, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list = view.findViewById(R.id.capabilityList)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------------- rendering ----------------

    private fun render() {
        if (!isAdded) return

        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        list.removeAllViews()

        PermissionManager.capabilities().forEach { capability ->
            val status = PermissionManager.status(context, capability)
            val row = inflater.inflate(R.layout.item_capability, list, false)

            row.findViewById<TextView>(R.id.tvCapLabel).setText(capability.label)
            row.findViewById<TextView>(R.id.tvCapExplain).setText(capability.explanation)

            val badge = row.findViewById<TextView>(R.id.tvCapStatus)
            badge.setText(getString(statusLabel(status)))
            badge.setTextColor(ContextCompat.getColor(context, statusColor(status)))

            val how = row.findViewById<TextView>(R.id.tvCapHow)
            how.setText(getString(actionLabel(capability, status)))

            if (isActionable(capability, status)) {
                row.setOnClickListener { act(capability) }
            } else {
                row.isClickable = false
            }

            list.addView(row)
        }
    }

    private fun statusLabel(status: CapabilityStatus): Int = when (status) {
        CapabilityStatus.ALLOWED -> R.string.status_allowed
        CapabilityStatus.LIMITED -> R.string.status_limited
        CapabilityStatus.DENIED -> R.string.status_denied
        CapabilityStatus.NOT_CONNECTED -> R.string.status_not_connected
        CapabilityStatus.UNSUPPORTED -> R.string.status_unsupported
    }

    private fun statusColor(status: CapabilityStatus): Int = when (status) {
        CapabilityStatus.ALLOWED -> R.color.status_ok
        CapabilityStatus.LIMITED -> R.color.status_warn
        else -> R.color.status_off
    }

    private fun actionLabel(capability: Capability, status: CapabilityStatus): Int = when {
        status == CapabilityStatus.UNSUPPORTED -> R.string.action_unsupported_here
        capability.gate == Gate.ALWAYS -> R.string.action_none_needed
        capability.gate == Gate.SYSTEM_SETTINGS -> R.string.action_open_settings
        status == CapabilityStatus.ALLOWED -> R.string.action_open_settings
        else -> R.string.action_grant
    }

    private fun isActionable(capability: Capability, status: CapabilityStatus): Boolean =
        status != CapabilityStatus.UNSUPPORTED && capability.gate != Gate.ALWAYS

    // ---------------- acting ----------------

    private fun act(capability: Capability) {
        val context = requireContext()

        // Capabilities Android only lets the user grant go straight to settings.
        PermissionManager.settingsIntent(context, capability)?.let { intent ->
            startActivity(intent)
            return
        }

        val status = PermissionManager.status(context, capability)

        // Already granted, or nothing left to ask: send them to app info, which
        // is the only place a granted permission can be revoked.
        if (status == CapabilityStatus.ALLOWED || capability.permissions.isEmpty()) {
            startActivity(PermissionManager.appDetailsIntent(context))
            return
        }

        requestPermissions.launch(capability.permissions.toTypedArray())
    }
}
