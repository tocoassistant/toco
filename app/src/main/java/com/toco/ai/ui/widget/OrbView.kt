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

/** The shared TOCO mascot used by the app, assistant session and TOCO Island. */
class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, THINKING, WORKING, SPEAKING, ERROR }

    /** Island personality states. Phase 2 will decide when these fire. */
    enum class Mood { IDLE, SLEEP, YAWN, WINK, PEEK, THINK, DIZZY, PROUD, ANNOYED, LOVE }

    private var state = State.IDLE
    private var mood = Mood.IDLE
    private var level = 0f
    private var targetLevel = 0f
    private var phase = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#160D28")
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

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
        mood = when (newState) {
            State.THINKING, State.WORKING -> Mood.THINK
            State.ERROR -> Mood.ANNOYED
            else -> Mood.IDLE
        }
        if (newState != State.LISTENING) targetLevel = 0f
        invalidate()
    }

    fun currentState(): State = state

    fun setMood(newMood: Mood) {
        if (mood == newMood) return
        mood = newMood
        invalidate()
    }

    fun currentMood(): Mood = mood

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
        val moodScale = when (mood) {
            Mood.SLEEP -> 0.92f
            Mood.YAWN -> 1f + 0.04f * sin(phase * 0.6f)
            Mood.DIZZY -> 1f + 0.03f * sin(phase * 5f)
            Mood.PROUD -> 1.05f
            else -> 1f
        }
        val breathe = 1f + 0.05f * sin(phase * 1.4f)
        val pulse = if (state == State.LISTENING) 1f + level * 0.35f else 1f
        val radius = base * breathe * pulse * moodScale

        drawGlow(canvas, cx, cy, radius * 2.1f, moodGlow(glow))
        when (state) {
            State.SPEAKING -> drawRipples(canvas, cx, cy, base, speakGlow)
            State.LISTENING -> drawListenRing(canvas, cx, cy, radius, listenCore)
            State.THINKING, State.WORKING -> drawParticles(canvas, cx, cy, base, core)
            else -> if (mood == Mood.THINK || mood == Mood.DIZZY) drawParticles(canvas, cx, cy, base, core)
        }
        drawCore(canvas, cx, cy, radius, moodCore(core), moodGlow(glow))
        drawMoodFace(canvas, cx, cy, radius)
    }

    private fun drawMoodFace(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        if (width < 28 || height < 28) return
        facePaint.strokeWidth = (r * 0.12f).coerceAtLeast(2f)
        val eyeY = cy - r * 0.05f
        val dx = r * 0.32f
        val eye = r * 0.13f
        when (mood) {
            Mood.SLEEP -> {
                canvas.drawLine(cx - dx - eye, eyeY, cx - dx + eye, eyeY, facePaint)
                canvas.drawLine(cx + dx - eye, eyeY, cx + dx + eye, eyeY, facePaint)
            }
            Mood.WINK -> {
                canvas.drawCircle(cx - dx, eyeY, eye * 0.65f, facePaint)
                canvas.drawLine(cx + dx - eye, eyeY, cx + dx + eye, eyeY, facePaint)
            }
            Mood.PEEK -> {
                canvas.drawCircle(cx - dx, eyeY, eye * 0.75f, facePaint)
                canvas.drawCircle(cx + dx, eyeY, eye * 0.75f, facePaint)
            }
            Mood.LOVE -> {
                canvas.drawCircle(cx - dx, eyeY, eye, facePaint)
                canvas.drawCircle(cx + dx, eyeY, eye, facePaint)
            }
            Mood.ANNOYED -> {
                canvas.drawLine(cx - dx - eye, eyeY - eye, cx - dx + eye, eyeY + eye * 0.3f, facePaint)
                canvas.drawLine(cx + dx - eye, eyeY + eye * 0.3f, cx + dx + eye, eyeY - eye, facePaint)
            }
            Mood.DIZZY -> {
                canvas.drawLine(cx - dx - eye, eyeY - eye, cx - dx + eye, eyeY + eye, facePaint)
                canvas.drawLine(cx - dx + eye, eyeY - eye, cx - dx - eye, eyeY + eye, facePaint)
                canvas.drawLine(cx + dx - eye, eyeY - eye, cx + dx + eye, eyeY + eye, facePaint)
                canvas.drawLine(cx + dx + eye, eyeY - eye, cx + dx - eye, eyeY + eye, facePaint)
            }
            else -> {
                canvas.drawCircle(cx - dx, eyeY, eye * 0.55f, facePaint)
                canvas.drawCircle(cx + dx, eyeY, eye * 0.55f, facePaint)
            }
        }
    }

    private fun moodCore(default: Int): Int = when (mood) {
        Mood.LOVE -> Color.parseColor("#FF9BE8")
        Mood.ANNOYED -> Color.parseColor("#FF9B9B")
        Mood.SLEEP -> Color.parseColor("#8F86C8")
        Mood.PROUD -> Color.parseColor("#D4C4FF")
        else -> default
    }

    private fun moodGlow(default: Int): Int = when (mood) {
        Mood.LOVE -> Color.parseColor("#9B3F8C")
        Mood.ANNOYED -> Color.parseColor("#8C3F5B")
        Mood.SLEEP -> Color.parseColor("#39345F")
        else -> default
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, r: Float, glow: Int) {
        glowPaint.shader = RadialGradient(cx, cy, r, intArrayOf(withAlpha(glow, 150), withAlpha(glow, 40), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, glowPaint)
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, r: Float, core: Int, glow: Int) {
        paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, intArrayOf(Color.WHITE, core, glow), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
    }

    private fun drawListenRing(canvas: Canvas, cx: Float, cy: Float, r: Float, core: Int) {
        ringPaint.shader = SweepGradient(cx, cy, intArrayOf(withAlpha(core, 0), withAlpha(core, 220), withAlpha(core, 0)), floatArrayOf(0f, 0.5f, 1f))
        ringPaint.strokeWidth = 6f + level * 10f
        val gap = r * (1.35f - level * 0.2f)
        canvas.save(); canvas.rotate(Math.toDegrees(phase.toDouble()).toFloat(), cx, cy); canvas.drawCircle(cx, cy, gap, ringPaint); canvas.restore()
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
            val rr = base * (1f + t * 1.4f)
            ringPaint.strokeWidth = 4f
            ringPaint.color = withAlpha(glow, (180 * (1f - t)).toInt())
            canvas.drawCircle(cx, cy, rr, ringPaint)
        }
    }

    private fun coreColor(s: State): Int = when (s) {
        State.IDLE -> idleCore; State.LISTENING -> listenCore; State.THINKING -> thinkCore
        State.WORKING -> workCore; State.SPEAKING -> speakCore; State.ERROR -> errorCore
    }

    private fun glowColor(s: State): Int = when (s) {
        State.IDLE -> idleGlow; State.LISTENING -> listenGlow; State.THINKING -> thinkGlow
        State.WORKING -> workGlow; State.SPEAKING -> speakGlow; State.ERROR -> errorGlow
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
