package com.toco.ai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.toco.ai.R
import com.toco.ai.ui.access.AccessFragment
import com.toco.ai.ui.common.SimpleFragment
import com.toco.ai.ui.home.HomeFragment
import com.toco.ai.ui.models.ModelsFragment
import com.toco.ai.ui.nav.TocoBottomNav

/**
 * Single-activity shell. Every page is a fragment swapped into pageContainer,
 * so the nav never rebuilds and its animation is never interrupted.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var nav: TocoBottomNav

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nav = findViewById(R.id.bottomNav)
        nav.setTabs(TABS) { index -> showPage(index) }

        val start = savedInstanceState?.getInt(KEY_TAB) ?: HOME
        nav.select(start, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, nav.selectedIndex())
    }

    /** Opens Access as an overlay page; system back returns to the current tab. */
    fun openAccess() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.pageContainer, AccessFragment(), "page_access")
            .addToBackStack("access")
            .commit()
    }

    private fun showPage(index: Int) {
        val tag = "page_$index"

        // Reuse an existing instance so page state survives tab switching.
        val existing = supportFragmentManager.findFragmentByTag(tag)
        val fragment = existing ?: newPage(index)

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.pageContainer, fragment, tag)
            .commit()
    }

    private fun newPage(index: Int): Fragment = when (index) {
        TASK -> SimpleFragment.create(R.string.task_title, R.string.task_body)
        MODELS -> ModelsFragment()
        HOME -> HomeFragment()
        ANALYSE -> SimpleFragment.create(R.string.analyse_title, R.string.analyse_body)
        else -> AccessFragment()
    }

    private companion object {
        const val KEY_TAB = "toco.selected_tab"

        const val TASK = 0
        const val MODELS = 1
        const val HOME = 2
        const val ANALYSE = 3

        val TABS = listOf(
            TocoBottomNav.Tab(android.R.drawable.ic_menu_agenda, R.string.nav_task),
            TocoBottomNav.Tab(android.R.drawable.ic_menu_gallery, R.string.nav_models),
            TocoBottomNav.Tab(android.R.drawable.ic_menu_compass, R.string.nav_home),
            TocoBottomNav.Tab(android.R.drawable.ic_menu_search, R.string.nav_analyse),
            TocoBottomNav.Tab(android.R.drawable.ic_menu_manage, R.string.nav_settings)
        )
    }
}
