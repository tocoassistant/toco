package com.toco.ai.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isActive = false
    private var pulseScale = 1f
    private var targetPulse = 1f
    private var rotationAngle = 0f
    private var currentSpeed = 0f
    private val maxSpeed = 2.8f
    private val acceleration = 0.08f
    private val deceleration = 0.05f

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4C4FF")
        alpha = 90
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9B7CFF")
        maskFilter = BlurMaskFilter(60f, BlurMaskFilter.Blur.NORMAL)
    }

    private val animationTick = object : Runnable {
        override fun run() {
            if (isActive) {
                if (currentSpeed < maxSpeed) currentSpeed += acceleration
                targetPulse = 1.08f
            } else {
                if (currentSpeed > 0f) currentSpeed -= deceleration
                if (currentSpeed < 0f) currentSpeed = 0f
                targetPulse = 1f
            }

            pulseScale += (targetPulse - pulseScale) * 0.08f
            rotationAngle = (rotationAngle + currentSpeed) % 360f
            invalidate()

            if (isActive || currentSpeed > 0f || Math.abs(pulseScale - 1f) > 0.01f) {
                postOnAnimation(this)
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        post { startIdleBreathing() }
    }

    private fun startIdleBreathing() {
        targetPulse = 1.03f
        postOnAnimation(animationTick)
    }

    fun activate() {
        isActive = true
        postOnAnimation(animationTick)
    }

    fun deactivate() {
        isActive = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (Math.min(width, height) / 2f) * 0.72f
        val radius = baseRadius * pulseScale

        glowPaint.alpha = if (isActive || currentSpeed > 0f) 70 else 30
        canvas.drawCircle(cx, cy, radius * 1.25f, glowPaint)

        corePaint.shader = RadialGradient(
            cx - radius * 0.25f, cy - radius * 0.3f, radius * 1.4f,
            intArrayOf(
                Color.parseColor("#F5EFFF"),
                Color.parseColor("#9B7CFF"),
                Color.parseColor("#5B3FA0")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, corePaint)

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        val blobRadius = radius * 0.32f
        for (i in 0 until 3) {
            val angle = Math.toRadians((i * 120).toDouble())
            val bx = cx + (radius * 0.35f) * Math.cos(angle).toFloat()
            val by = cy + (radius * 0.35f) * Math.sin(angle).toFloat()
            canvas.drawCircle(bx, by, blobRadius, swirlPaint)
        }
        canvas.restore()
    }
}
