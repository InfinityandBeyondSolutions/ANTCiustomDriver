// AlphabetIndexView.kt
package com.ibs.ibs_antdrivers.ui.phonebook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.ibs.ibs_antdrivers.R
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class AlphabetIndexView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sections: List<Char> = (('A'..'Z') + '#').toList()
    private var onSelect: ((Char) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f * resources.displayMetrics.scaledDensity / 2f
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
    }
    private val textBounds = Rect()

    fun setSections(chars: List<Char>) {
        sections = if (chars.isNotEmpty()) chars else (('A'..'Z') + '#').toList()
        invalidate()
    }

    fun setOnLetterSelected(cb: (Char) -> Unit) {
        onSelect = cb
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sections.isEmpty()) return

        val slot = height.toFloat() / sections.size
        val cx = width / 2f

        sections.forEachIndexed { idx, ch ->
            val y = slot * idx + slot / 2f + textAscentAdjust()
            canvas.drawText(ch.toString(), cx, y, paint)
        }
    }

    private fun textAscentAdjust(): Float {
        paint.getTextBounds("A", 0, 1, textBounds)
        return textBounds.height() / 2f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Minimum comfy width ~28dp
        val minW = (28 * resources.displayMetrics.density).toInt()
        val w = resolveSize(minW, widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sections.isEmpty()) return false
        val slot = height.toFloat() / sections.size
        val idx = floor(event.y / slot).toInt().coerceIn(0, sections.lastIndex)
        val ch = sections[idx]
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                onSelect?.invoke(ch)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
