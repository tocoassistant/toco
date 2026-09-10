package com.toco.ai.ui.models

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.toco.ai.R
import com.toco.ai.core.Prefs
import com.toco.ai.core.Voices
import com.toco.ai.skill.SkillRegistry

/**
 * Models: pick TOCO's voice, and see every command module that's loaded.
 *
 * The module list reads straight from SkillRegistry, so anything registered at
 * runtime shows up here with no extra work.
 */
class ModelsFragment : Fragment() {

    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_models, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        renderVoices(view)
        renderModules(view)
    }

    private fun renderVoices(root: View) {
        val list = root.findViewById<LinearLayout>(R.id.voiceList)
        val note = root.findViewById<TextView>(R.id.tvVoiceNote)
        val inflater = LayoutInflater.from(requireContext())

        // Be truthful about what actually differs between the options.
        val engineVoices = Voices.availableVoiceCount()
        note.setText(
            if (engineVoices > 1) getString(R.string.voice_multi_engine, engineVoices)
            else getString(R.string.voice_single_engine)
        )

        list.removeAllViews()

        Voices.options.forEach { option ->
            val row = inflater.inflate(R.layout.item_voice, list, false)

            row.findViewById<TextView>(R.id.voiceLabel).setText(option.label)
            row.findViewById<TextView>(R.id.voiceDesc).setText(option.description)

            val selected = option.id == prefs.voiceId
            row.findViewById<View>(R.id.voiceRing).alpha = if (selected) 1f else 0.25f
            row.findViewById<TextView>(R.id.voiceLabel).setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.accent_light else R.color.text_primary
                )
            )

            row.setOnClickListener {
                prefs.voiceId = option.id
                Voices.apply(requireContext(), option)
                renderVoices(root)
            }

            row.findViewById<TextView>(R.id.voicePlay).setOnClickListener {
                Voices.preview(requireContext(), option)
            }

            list.addView(row)
        }
    }

    private fun renderModules(root: View) {
        val list = root.findViewById<LinearLayout>(R.id.modelList)
        val count = root.findViewById<TextView>(R.id.tvModelCount)
        val skills = SkillRegistry.all()
        val inflater = LayoutInflater.from(requireContext())

        count.setText(getString(R.string.models_count, skills.size))
        list.removeAllViews()

        skills.forEach { skill ->
            val row = inflater.inflate(R.layout.item_model, list, false)
            row.findViewById<TextView>(R.id.tvModelName).setText(skill.name)
            row.findViewById<TextView>(R.id.tvModelId).setText(skill.id)
            list.addView(row)
        }
    }
}
