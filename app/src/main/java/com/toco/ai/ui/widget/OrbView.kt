package com.toco.ai.ui.widget

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The TOCO orb.
 *
 * Three visual states, one animation loop. The loop parks itself when there is
 * nothing left to animate, so an idle orb costs no frames.
 */
class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, WORKING }

    private var state = State.IDLE

    // --- animation ---
    private var pulse = 1f
    private var targetPulse = IDLE_PULSE
    private var angle = 0f
    private var speed = 0f
    private var targetSpeed = 0f

    // --- paints ---
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4C4FF")
        alpha = 90
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9B7CFF")
        maskFilter = BlurMaskFilter(60f, BlurMaskFilter.Blur.NORMAL)
    }

    private val tick = object : Runnable {
        override fun run() {
            step()
            invalidate()
            if (isAnimating()) postOnAnimation(this)
        }
    }

    init {
        // BlurMaskFilter is not supported in hardware layers.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        post { start() }
    }

    // ---------------- public API ----------------

    fun setState(newState: State) {
        if (state == newState) return
        state = newState
        when (state) {
            State.IDLE -> {
                targetSpeed = 0f
                targetPulse = IDLE_PULSE
            }
            State.LISTENING -> {
                targetSpeed = LISTEN_SPEED
                targetPulse = LISTEN_PULSE
            }
            State.WORKING -> {
                targetSpeed = WORK_SPEED
                targetPulse = WORK_PULSE
            }
        }
        start()
    }

    fun currentState(): State = state

    // ---------------- internals ----------------

    private fun start() = postOnAnimation(tick)

    private fun step() {
        speed += (targetSpeed - speed) * SPEED_EASE
        if (abs(targetSpeed - speed) < 0.01f) speed = targetSpeed

        pulse += (targetPulse - pulse) * PULSE_EASE
        angle = (angle + speed) % 360f
    }

    private fun isAnimating(): Boolean =
        speed > 0.01f || abs(pulse - targetPulse) > 0.001f

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val base = (min(width, height) / 2f) * 0.72f
        val r = base * pulse

        // outer glow
        glowPaint.alpha = if (state == State.IDLE) 30 else 70
        canvas.drawCircle(cx, cy, r * 1.25f, glowPaint)

        // core sphere
        corePaint.shader = RadialGradient(
            cx - r * 0.25f,
            cy - r * 0.30f,
            r * 1.4f,
            intArrayOf(
                Color.parseColor("#F5EFFF"),
                Color.parseColor("#9B7CFF"),
                Color.parseColor("#5B3FA0")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, corePaint)

        // rotating inner swirl
        canvas.save()
        canvas.rotate(angle, cx, cy)
        val blob = r * 0.32f
        val orbit = r * 0.35f
        for (i in 0 until SWIRL_COUNT) {
            val a = Math.toRadians((i * (360.0 / SWIRL_COUNT)))
            canvas.drawCircle(
                cx + orbit * cos(a).toFloat(),
                cy + orbit * sin(a).toFloat(),
                blob,
                swirlPaint
            )
        }
        canvas.restore()
    }

    private companion object {
        const val IDLE_PULSE = 1.03f
        const val LISTEN_PULSE = 1.08f
        const val WORK_PULSE = 1.05f

        const val LISTEN_SPEED = 2.8f
        const val WORK_SPEED = 1.4f

        const val SPEED_EASE = 0.06f
        const val PULSE_EASE = 0.08f

        const val SWIRL_COUNT = 3
    }
}
