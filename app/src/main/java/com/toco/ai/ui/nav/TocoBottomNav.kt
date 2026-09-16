package com.toco.ai.ui.nav

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.toco.ai.R

/**
 * Bottom navigation where the raised ring follows the selected tab.
 *
 * There is no single circle being moved around. Every tab owns its own ring,
 * hidden at 70% scale and zero alpha. Selecting a tab fades and scales that
 * ring in while lifting the icon into it; deselecting reverses it. Two
 * independent animations, so they can overlap without fighting each other.
 */
class TocoBottomNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** One tab: an icon, a label, and nothing else. */
    data class Tab(val iconRes: Int, val labelRes: Int)

    private class Holder(
        val root: FrameLayout,
        val content: LinearLayout,
        val ring: View,
        val icon: ImageView,
        val label: TextView
    )

    private val holders = mutableListOf<Holder>()
    private var selected = -1
    private var onSelect: ((Int) -> Unit)? = null

    private val liftPx: Float
    private val accent: Int
    private val dim: Int

    init {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
        liftPx = resources.getDimension(R.dimen.nav_lift)
        accent = ContextCompat.getColor(context, R.color.accent)
        dim = ContextCompat.getColor(context, R.color.text_secondary)
    }

    /** Build the tabs. Call once, before [select]. */
    fun setTabs(tabs: List<Tab>, onSelect: (Int) -> Unit) {
        this.onSelect = onSelect
        removeAllViews()
        holders.clear()

        val inflater = LayoutInflater.from(context)

        tabs.forEachIndexed { index, tab ->
            val item = inflater.inflate(R.layout.view_nav_item, this, false) as FrameLayout

            val holder = Holder(
                root = item,
                content = item.findViewById(R.id.navContent),
                ring = item.findViewById(R.id.navRing),
                icon = item.findViewById(R.id.navIcon),
                label = item.findViewById(R.id.navLabel)
            )

            holder.icon.setImageResource(tab.iconRes)
            holder.label.setText(context.getString(tab.labelRes))

            // Start hidden; select() decides what becomes visible.
            holder.ring.alpha = 0f
            holder.ring.scaleX = RING_MIN
            holder.ring.scaleY = RING_MIN
            holder.icon.setColorFilter(dim)

            item.setOnClickListener {
                // Fast switching between different tabs is fine; repeatedly
                // hitting the same one is not, and select() already ignores
                // that. This only stops a burst from queueing transactions.
                if (com.toco.ai.util.Taps.allow("nav", 250L)) select(index)
            }

            addView(item, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            holders += holder
        }
    }

    /** Selecting the tab already selected is ignored, so taps can't stack animations. */
    fun select(index: Int, animate: Boolean = true) {
        if (index !in holders.indices || index == selected) return

        val previous = selected
        selected = index

        if (previous in holders.indices) deactivate(holders[previous], animate)
        activate(holders[index], animate)

        onSelect?.invoke(index)
    }

    fun selectedIndex(): Int = selected

    // ---------------- animation ----------------

    private fun activate(h: Holder, animate: Boolean) {
        h.icon.setColorFilter(accent)

        if (!animate) {
            h.ring.alpha = 1f
            h.ring.scaleX = RING_MAX
            h.ring.scaleY = RING_MAX
            h.content.translationY = -liftPx
            h.label.alpha = 0f
            return
        }

        h.ring.animate()
            .alpha(1f)
            .scaleX(RING_MAX)
            .scaleY(RING_MAX)
            .setDuration(IN_MS)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        h.content.animate()
            .translationY(-liftPx)
            .setDuration(IN_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        h.label.animate().alpha(0f).setDuration(140).start()
    }

    private fun deactivate(h: Holder, animate: Boolean) {
        h.icon.setColorFilter(dim)

        if (!animate) {
            h.ring.alpha = 0f
            h.ring.scaleX = RING_MIN
            h.ring.scaleY = RING_MIN
            h.content.translationY = 0f
            h.label.alpha = 1f
            return
        }

        h.ring.animate()
            .alpha(0f)
            .scaleX(RING_MIN)
            .scaleY(RING_MIN)
            .setDuration(OUT_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        h.content.animate()
            .translationY(0f)
            .setDuration(OUT_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        h.label.animate().alpha(1f).setDuration(OUT_MS).start()
    }

    private companion object {
        const val RING_MIN = 0.7f
        const val RING_MAX = 1.45f
        const val IN_MS = 280L
        const val OUT_MS = 200L
    }
}
