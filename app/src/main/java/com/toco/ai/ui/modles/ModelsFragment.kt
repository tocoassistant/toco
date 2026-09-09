package com.toco.ai.ui.models

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.toco.ai.R
import com.toco.ai.skill.SkillRegistry

/**
 * Lists every skill currently registered — built-in today, AI-generated later.
 * Reads straight from SkillRegistry, so a generated module shows up here with
 * no extra work.
 */
class ModelsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_models, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list = view.findViewById<LinearLayout>(R.id.modelList)
        val count = view.findViewById<TextView>(R.id.tvModelCount)
        val skills = SkillRegistry.all()

        count.setText(getString(R.string.models_count, skills.size))

        val inflater = LayoutInflater.from(requireContext())
        list.removeAllViews()

        skills.forEach { skill ->
            val row = inflater.inflate(R.layout.item_model, list, false)
            row.findViewById<TextView>(R.id.tvModelName).setText(skill.name)
            row.findViewById<TextView>(R.id.tvModelId).setText(skill.id)
            list.addView(row)
        }
    }
}