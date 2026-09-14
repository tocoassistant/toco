package com.toco.ai.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.toco.ai.R
import com.toco.ai.skill.SkillResult

/**
 * "Which one did you mean?" in TOCO's own styling.
 *
 * The default AlertDialog list was grey with system fonts and looked like it
 * belonged to a different app. Since this is one of the few moments TOCO
 * interrupts the user, it is worth matching the rest of the interface.
 *
 * Each row shows the saved name, the number, and its own action button, so the
 * choice is unambiguous — two contacts called "Ma" are told apart by the
 * number, not by guessing at row order.
 */
object ChooserDialog {

    fun show(
        context: Context,
        choice: SkillResult.Choose,
        actionLabel: String,
        onPick: (SkillResult.Choose.Option) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_choose, null, false)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.chooseTitle).setText(choice.prompt)
        view.findViewById<TextView>(R.id.chooseSubtitle).setText(
            context.getString(R.string.choose_subtitle, choice.options.size)
        )

        val list = view.findViewById<LinearLayout>(R.id.chooseList)
        val inflater = LayoutInflater.from(context)
        list.removeAllViews()

        choice.options.forEach { option ->
            val row = inflater.inflate(R.layout.item_choose_option, list, false)

            row.findViewById<TextView>(R.id.optionLabel).setText(option.label)
            row.findViewById<TextView>(R.id.optionDetail).setText(option.detail)
            row.findViewById<TextView>(R.id.optionInitial)
                .setText(option.label.trim().take(1).uppercase())
            row.findViewById<TextView>(R.id.optionGo).setText(actionLabel)

            row.setOnClickListener {
                dialog.dismiss()
                onPick(option)
            }

            list.addView(row)
        }

        view.findViewById<TextView>(R.id.chooseCancel).setOnClickListener { dialog.dismiss() }

        // Transparent window so the card's own rounded background is what shows,
        // instead of sitting on the platform's grey panel.
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.75f)
        }

        dialog.show()
    }
}
