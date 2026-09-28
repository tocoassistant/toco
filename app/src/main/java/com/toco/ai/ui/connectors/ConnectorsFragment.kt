package com.toco.ai.ui.connectors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.toco.ai.R
import com.toco.ai.core.EventLog

/** Connector control centre. OAuth credentials themselves belong in Supabase, never on-device. */
class ConnectorsFragment : Fragment() {
    private lateinit var scope: RadioGroup

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_connectors, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        scope = view.findViewById(R.id.connectorScope)
        val saved = requireContext().getSharedPreferences("toco_connectors", 0).getString("scope", "read")
        when (saved) { "draft" -> scope.check(R.id.scopeDraft); "act" -> scope.check(R.id.scopeAct); else -> scope.check(R.id.scopeRead) }
        scope.setOnCheckedChangeListener { _, id ->
            val value = when (id) { R.id.scopeDraft -> "draft"; R.id.scopeAct -> "act"; else -> "read" }
            requireContext().getSharedPreferences("toco_connectors", 0).edit().putString("scope", value).apply()
            EventLog.log(requireContext(), "CONNECTOR", "permission scope set to $value")
        }

        listOf(R.id.connectGmail, R.id.connectDiscord, R.id.connectInstagram, R.id.connectWhatsApp)
            .forEach { id -> view.findViewById<Button>(id).setOnClickListener {
                Toast.makeText(requireContext(), "Secure account linking will open here when provider OAuth is enabled in Supabase.", Toast.LENGTH_LONG).show()
            }}
    }
}
