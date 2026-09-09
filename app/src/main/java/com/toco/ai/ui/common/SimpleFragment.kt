package com.toco.ai.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.toco.ai.R

/**
 * One class for every page that is still just a title and a line of text.
 * Beats writing four near-identical fragments, and each becomes a real page
 * by swapping it out here when its features land.
 */
class SimpleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_simple, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        view.findViewById<TextView>(R.id.tvTitle).setText(getString(args.getInt(ARG_TITLE)))
        view.findViewById<TextView>(R.id.tvBody).setText(getString(args.getInt(ARG_BODY)))
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_BODY = "body"

        fun create(titleRes: Int, bodyRes: Int) = SimpleFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_TITLE, titleRes)
                putInt(ARG_BODY, bodyRes)
            }
        }
    }
}
