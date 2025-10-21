package com.ibs.ibs_antdrivers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class StrokeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeColor: Int = Color.BLACK
    private var strokeWidthPx: Float = dpToPx(1.25f) // thin but visible default

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // keep strokes from disappearing
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.StrokeTextView)
            strokeColor = a.getColor(R.styleable.StrokeTextView_strokeColor, Color.BLACK)
            if (a.hasValue(R.styleable.StrokeTextView_strokeWidthDp)) {
                strokeWidthPx = dpToPx(a.getFloat(R.styleable.StrokeTextView_strokeWidthDp, 1.25f))
            }
            a.recycle()
        }
        paint.isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        val tp = paint

        // 1) Draw normal fill (your yellow)
        tp.style = Paint.Style.FILL
        super.onDraw(canvas)

        // 2) Draw stroke on TOP so it's visible
        val savedColor = tp.color
        val savedBold = tp.isFakeBoldText
        val savedWidth = tp.strokeWidth

        tp.style = Paint.Style.STROKE
        tp.strokeJoin = Paint.Join.ROUND
        tp.strokeMiter = 10f
        tp.strokeWidth = strokeWidthPx.coerceAtLeast(1.2f)
        tp.color = strokeColor
        tp.isFakeBoldText = false // avoid thickening that can hide stroke

        // draw again (stroke pass)
        super.onDraw(canvas)

        // restore
        tp.style = Paint.Style.FILL
        tp.color = savedColor
        tp.isFakeBoldText = savedBold
        tp.strokeWidth = savedWidth
    }

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density
}
