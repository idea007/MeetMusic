package com.dafay.demo.exoplayer.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import kotlin.math.PI
import kotlin.math.sin

class PlayingBarsDrawable(
    @ColorInt barColor: Int = Color.WHITE
) : Drawable(), Animatable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barColor
        style = Paint.Style.FILL
    }
    private val barRect = RectF()
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ANIMATION_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidateSelf()
        }
    }

    fun setBarColor(@ColorInt color: Int) {
        paint.color = color
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        val drawableWidth = bounds.width().toFloat()
        val drawableHeight = bounds.height().toFloat()
        val barWidth = drawableWidth * BAR_WIDTH_RATIO
        val gap = drawableWidth * BAR_GAP_RATIO
        val totalWidth = barWidth * BAR_COUNT + gap * (BAR_COUNT - 1)
        val startX = bounds.left + (drawableWidth - totalWidth) / 2f
        val bottom = bounds.bottom - drawableHeight * VERTICAL_PADDING_RATIO
        val minHeight = drawableHeight * MIN_BAR_HEIGHT_RATIO
        val maxHeight = drawableHeight * MAX_BAR_HEIGHT_RATIO

        for (index in 0 until BAR_COUNT) {
            val phase = progress + index * PHASE_OFFSET
            val wave = ((sin(phase * TWO_PI) + 1f) / 2f).toFloat()
            val barHeight = minHeight + (maxHeight - minHeight) * wave
            val left = startX + index * (barWidth + gap)
            val top = bottom - barHeight

            barRect.set(left, top, left + barWidth, bottom)
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return INTRINSIC_SIZE
    }

    override fun getIntrinsicHeight(): Int {
        return INTRINSIC_SIZE
    }

    override fun start() {
        if (!animator.isStarted) {
            animator.start()
        }
    }

    override fun stop() {
        animator.cancel()
        progress = 0f
        invalidateSelf()
    }

    override fun isRunning(): Boolean {
        return animator.isRunning
    }

    companion object {
        private const val BAR_COUNT = 3
        private const val INTRINSIC_SIZE = 24
        private const val ANIMATION_DURATION_MS = 900L
        private const val BAR_WIDTH_RATIO = 0.14f
        private const val BAR_GAP_RATIO = 0.13f
        private const val VERTICAL_PADDING_RATIO = 0.18f
        private const val MIN_BAR_HEIGHT_RATIO = 0.24f
        private const val MAX_BAR_HEIGHT_RATIO = 0.68f
        private const val PHASE_OFFSET = 0.28f
        private const val TWO_PI = (PI * 2).toFloat()
    }
}
