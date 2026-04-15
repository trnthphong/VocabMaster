package com.example.vocabmaster.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.FrameLayout
import com.example.vocabmaster.R

class WaveCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val pathMain = Path()
    private val pathShadow = Path()

    private var isBottomWave = false

    private val paintMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4046d8")
        style = Paint.Style.FILL
    }

    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#353BAF")
        style = Paint.Style.FILL
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.WaveCardView)
        isBottomWave = a.getBoolean(R.styleable.WaveCardView_isBottomWave, false)
        a.recycle()

        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (isBottomWave) {
            drawBottomWave(canvas, w, h)
        } else {
            drawTopWave(canvas, w, h)
        }
    }

    private fun drawTopWave(canvas: Canvas, w: Float, h: Float) {
        val waveBase = dpToPx(60f)
        val amplitude = dpToPx(40f)

        // Shadow Layer
        val offsetX = w * 0.2f
        pathShadow.reset()
        pathShadow.moveTo(-offsetX, h)
        pathShadow.lineTo(-offsetX, waveBase)
        pathShadow.cubicTo(
            w * 0.2f - offsetX, waveBase - amplitude,
            w * 0.4f - offsetX, waveBase + amplitude * 1.5f,
            w * 0.6f - offsetX, waveBase
        )
        pathShadow.cubicTo(
            w * 0.8f - offsetX, waveBase - amplitude * 0.8f,
            w * 0.9f - offsetX, waveBase - amplitude * 0.5f,
            w - offsetX, waveBase + dpToPx(10f)
        )
        pathShadow.lineTo(w, waveBase + dpToPx(10f))
        pathShadow.lineTo(w, h)
        pathShadow.close()
        canvas.drawPath(pathShadow, paintShadow)

        // Main Layer
        pathMain.reset()
        pathMain.moveTo(0f, h)
        pathMain.lineTo(0f, waveBase)
        pathMain.cubicTo(
            w * 0.2f, waveBase - amplitude,
            w * 0.4f, waveBase + amplitude * 1.5f,
            w * 0.6f, waveBase
        )
        pathMain.cubicTo(
            w * 0.8f, waveBase - amplitude * 0.8f,
            w * 0.9f, waveBase - amplitude * 0.5f,
            w, waveBase + dpToPx(10f)
        )
        pathMain.lineTo(w, h)
        pathMain.close()
        canvas.drawPath(pathMain, paintMain)
    }

    private fun drawBottomWave(canvas: Canvas, w: Float, h: Float) {
        val waveBase = h - dpToPx(60f)
        val amplitude = dpToPx(40f)

        // Shadow Layer (Inverted)
        val offsetX = w * 0.2f
        pathShadow.reset()
        pathShadow.moveTo(-offsetX, 0f)
        pathShadow.lineTo(-offsetX, waveBase)
        pathShadow.cubicTo(
            w * 0.2f - offsetX, waveBase + amplitude,
            w * 0.4f - offsetX, waveBase - amplitude * 1.5f,
            w * 0.6f - offsetX, waveBase
        )
        pathShadow.cubicTo(
            w * 0.8f - offsetX, waveBase + amplitude * 0.8f,
            w * 0.9f - offsetX, waveBase + amplitude * 0.5f,
            w - offsetX, waveBase - dpToPx(10f)
        )
        pathShadow.lineTo(w, waveBase - dpToPx(10f))
        pathShadow.lineTo(w, 0f)
        pathShadow.close()
        canvas.drawPath(pathShadow, paintShadow)

        // Main Layer (Inverted)
        pathMain.reset()
        pathMain.moveTo(0f, 0f)
        pathMain.lineTo(0f, waveBase)
        pathMain.cubicTo(
            w * 0.2f, waveBase + amplitude,
            w * 0.4f, waveBase - amplitude * 1.5f,
            w * 0.6f, waveBase
        )
        pathMain.cubicTo(
            w * 0.8f, waveBase + amplitude * 0.8f,
            w * 0.9f, waveBase + amplitude * 0.5f,
            w, waveBase - dpToPx(10f)
        )
        pathMain.lineTo(w, 0f)
        pathMain.close()
        canvas.drawPath(pathMain, paintMain)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(pathMain)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
