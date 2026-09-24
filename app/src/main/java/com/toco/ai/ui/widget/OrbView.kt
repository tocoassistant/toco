package com.toco.ai.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The face of TOCO: a living orb that reacts to what the assistant is doing.
 *
 * It is drawn from scratch every frame rather than assembled from static
 * images, so it can breathe, spin, ripple and, while listening, pulse in time
 * with the user's actual voice. Each state has its own character:
 *
 *   IDLE       slow breathing, a soft lavender core
 *   LISTENING  cyan, a rotating energy ring, a live pulse driven by mic level
 *   THINKING   particles orbiting the core, violet
 *   WORKING    the same, faster and tighter
 *   SPEAKING   concentric ripples flowing outward
 *   ERROR      a restrained red flush
 *
 * Everything is time-based off one always-running animator, so adding a state
 * never means wiring up another animation: the draw code just reads the clock
 * and the current state.
 */
class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, THINKING, WORKING, SPEAKING, ERROR }

    private var state = State.IDLE

    /** 0..1, driven by the microphone while listening; eased toward the target. */
    private var level = 0f
    private var targetLevel = 0f

    /** Monotonic phase in radians. One animator advances everything. */
    private var phase = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 4000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            level += (targetLevel - level) * 0.25f
            invalidate()
        }
    }

    private val idleCore = Color.parseColor("#B49CFF");  private val idleGlow = Color.parseColor("#5B3FA0")
    private val listenCore = Color.parseColor("#6FE9FF"); private val listenGlow = Color.parseColor("#1E7A99")
    private val thinkCore = Color.parseColor("#C08CFF");  private val thinkGlow = Color.parseColor("#5B2FA0")
    private val workCore = Color.parseColor("#B49CFF");   private val workGlow = Color.parseColor("#5B3FA0")
    private val speakCore = Color.parseColor("#D4C4FF");  private val speakGlow = Color.parseColor("#6B4FB0")
    private val errorCore = Color.parseColor("#FF7B7B");  private val errorGlow = Color.parseColor("#992E2E")

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setState(newState: State) {
        if (state == newState) return
        state = newState
        if (newState != State.LISTENING) targetLevel = 0f
        invalidate()
    }

    fun currentState(): State = state

    /**
     * Feed the mic level while listening, 0..1. Ignored unless actually
     * listening, so a stray value cannot make a resting orb twitch.
     */
    fun setAmplitude(value: Float) {
        if (state != State.LISTENING) return
        targetLevel = value.coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) / 2f * 0.62f

        val core = coreColor(state)
        val glow = glowColor(state)

        val breathe = 1f + 0.05f * sin(phase * 1.4f)
        val pulse = if (state == State.LISTENING) 1f + level * 0.35f else 1f
        val radius = base * breathe * pulse

        drawGlow(canvas, cx, cy, radius * 2.1f, glow)

        when (state) {
            State.SPEAKING -> drawRipples(canvas, cx, cy, base, speakGlow)
            State.LISTENING -> drawListenRing(canvas, cx, cy, radius, listenCore)
            State.THINKING, State.WORKING -> drawParticles(canvas, cx, cy, base, core)
            else -> {}
        }

        drawCore(canvas, cx, cy, radius, core, glow)
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, r: Float, glow: Int) {
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(withAlpha(glow, 150), withAlpha(glow, 40), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, glowPaint)
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, r: Float, core: Int, glow: Int) {
        paint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
            intArrayOf(Color.WHITE, core, glow),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
    }

    private fun drawListenRing(canvas: Canvas, cx: Float, cy: Float, r: Float, core: Int) {
        ringPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(withAlpha(core, 0), withAlpha(core, 220), withAlpha(core, 0)),
            floatArrayOf(0f, 0.5f, 1f)
        )
        ringPaint.strokeWidth = 6f + level * 10f
        val gap = r * (1.35f - level * 0.2f)
        canvas.save()
        canvas.rotate(Math.toDegrees(phase.toDouble()).toFloat(), cx, cy)
        canvas.drawCircle(cx, cy, gap, ringPaint)
        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas, cx: Float, cy: Float, base: Float, core: Int) {
        val count = 6
        val speed = if (state == State.WORKING) 2.2f else 1.3f
        val orbit = base * 1.5f
        for (i in 0 until count) {
            val a = phase * speed + (2 * Math.PI * i / count).toFloat()
            val px = cx + cos(a) * orbit
            val py = cy + sin(a) * orbit
            val fade = 0.4f + 0.6f * ((sin(a * 2) + 1) / 2).toFloat()
            particlePaint.shader = null
            particlePaint.color = withAlpha(core, (200 * fade).toInt())
            canvas.drawCircle(px, py, base * 0.12f, particlePaint)
        }
    }

    private fun drawRipples(canvas: Canvas, cx: Float, cy: Float, base: Float, glow: Int) {
        ringPaint.shader = null
        for (i in 0 until 3) {
            val t = (phase / (2 * Math.PI).toFloat() + i / 3f) % 1f
            val r = base * (1f + t * 1.4f)
            ringPaint.strokeWidth = 4f
            ringPaint.color = withAlpha(glow, (180 * (1f - t)).toInt())
            canvas.drawCircle(cx, cy, r, ringPaint)
        }
    }

    private fun coreColor(s: State): Int = when (s) {
        State.IDLE -> idleCore
        State.LISTENING -> listenCore
        State.THINKING -> thinkCore
        State.WORKING -> workCore
        State.SPEAKING -> speakCore
        State.ERROR -> errorCore
    }

    private fun glowColor(s: State): Int = when (s) {
        State.IDLE -> idleGlow
        State.LISTENING -> listenGlow
        State.THINKING -> thinkGlow
        State.WORKING -> workGlow
        State.SPEAKING -> speakGlow
        State.ERROR -> errorGlow
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
