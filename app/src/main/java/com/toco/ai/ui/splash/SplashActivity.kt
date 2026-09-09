package com.toco.ai.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.toco.ai.R
import com.toco.ai.ui.MainActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.splashLogo)
        val glow = findViewById<View>(R.id.splashGlow)

        prepare(logo, glow)
        animateIn(logo, glow)
    }

    private fun prepare(logo: View, glow: View) {
        logo.alpha = 0f
        logo.scaleX = 0.3f
        logo.scaleY = 0.3f

        glow.alpha = 0f
        glow.scaleX = 0.5f
        glow.scaleY = 0.5f
    }

    private fun animateIn(logo: View, glow: View) {
        glow.animate()
            .alpha(0.6f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setStartDelay(200)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction { handler.postDelayed(::goHome, HOLD_MS) }
            .start()
    }

    private fun goHome() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val HOLD_MS = 800L
    }
}
