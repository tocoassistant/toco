package com.toco.ai.ui.connectors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.toco.ai.R

class ConnectorsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_connectors, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        listOf(R.id.connectGmail, R.id.connectDiscord, R.id.connectInstagram, R.id.connectWhatsApp)
            .forEach { id -> view.findViewById<Button>(id).setOnClickListener {
                Toast.makeText(requireContext(), R.string.connector_coming, Toast.LENGTH_SHORT).show()
            }}
    }
}
