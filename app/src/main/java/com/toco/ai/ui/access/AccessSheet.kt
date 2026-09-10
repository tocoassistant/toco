package com.toco.ai.ui.access

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.toco.ai.R
import com.toco.ai.access.Capability
import com.toco.ai.access.CapabilityStatus
import com.toco.ai.access.Gate
import com.toco.ai.access.PermissionManager

/**
 * TOCO Access as a compact bottom overlay.
 *
 * One line per capability: a coloured dot, the name, the status. The long
 * explanations that made the full page heavy are moved to a toast on tap, so
 * the sheet stays scannable. A plain DialogFragment is used rather than
 * Material's BottomSheetDialogFragment to avoid adding a dependency.
 */
class AccessSheet : DialogFragment() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            render()
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_access, container, false)

    override fun onStart() {
        super.onStart()

        // Pin to the bottom edge, full width — what makes it read as a sheet.
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read on resume so returning from system settings shows the truth.
        render()
    }

    private fun render() {
        val view = view ?: return
        if (!isAdded) return

        val context = requireContext()
        val list = view.findViewById<LinearLayout>(R.id.accessList)
        val summary = view.findViewById<TextView>(R.id.tvAccessSummary)
        val inflater = LayoutInflater.from(context)

        list.removeAllViews()

        val capabilities = PermissionManager.capabilities()
        var allowed = 0

        capabilities.forEach { capability ->
            val status = PermissionManager.status(context, capability)
            if (status == CapabilityStatus.ALLOWED) allowed++

            val row = inflater.inflate(R.layout.item_access_row, list, false)

            row.findViewById<TextView>(R.id.rowLabel).setText(capability.label)

            val color = ContextCompat.getColor(context, colorFor(status))
            row.findViewById<View>(R.id.rowDot).setBackgroundColor(color)

            val statusView = row.findViewById<TextView>(R.id.rowStatus)
            statusView.setText(getString(labelFor(status)))
            statusView.setTextColor(color)

            if (status != CapabilityStatus.UNSUPPORTED && capability.gate != Gate.ALWAYS) {
                row.setOnClickListener { act(capability) }
            }

            list.addView(row)
        }

        summary.setText("$allowed of ${capabilities.size} allowed")
    }

    private fun labelFor(status: CapabilityStatus): Int = when (status) {
        CapabilityStatus.ALLOWED -> R.string.status_allowed
        CapabilityStatus.LIMITED -> R.string.status_limited
        CapabilityStatus.DENIED -> R.string.status_denied
        CapabilityStatus.NOT_CONNECTED -> R.string.status_not_connected
        CapabilityStatus.UNSUPPORTED -> R.string.status_unsupported
    }

    private fun colorFor(status: CapabilityStatus): Int = when (status) {
        CapabilityStatus.ALLOWED -> R.color.status_ok
        CapabilityStatus.LIMITED -> R.color.status_warn
        else -> R.color.status_off
    }

    private fun act(capability: Capability) {
        val context = requireContext()

        PermissionManager.settingsIntent(context, capability)?.let {
            startActivity(it)
            return
        }

        val status = PermissionManager.status(context, capability)

        // Granted, or nothing left to request: app info is the only way to change it.
        if (status == CapabilityStatus.ALLOWED || capability.permissions.isEmpty()) {
            startActivity(PermissionManager.appDetailsIntent(context))
            return
        }

        requestPermissions.launch(capability.permissions.toTypedArray())
    }

    companion object {
        const val TAG = "access_sheet"
    }
}
