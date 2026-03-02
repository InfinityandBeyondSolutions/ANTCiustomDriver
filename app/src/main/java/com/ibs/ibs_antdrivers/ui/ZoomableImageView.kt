package com.ibs.ibs_antdrivers.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * A simple ImageView that supports pinch-to-zoom and pan gestures.
 * Zoom range: 1× – 5×.  Double-tap resets to fit.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private val savedMatrix = Matrix()

    // Gesture tracking
    private var mode = NONE
    private val start = PointF()
    private val mid = PointF()
    private var dist = 1f
    private var scale = 1f
    private val minScale = 1f
    private val maxScale = 5f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    // Double-tap reset
    private var lastTapTime = 0L

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = matrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                start.set(event.x, event.y)
                mode = DRAG

                // Double-tap detection
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 300) {
                    resetZoom()
                }
                lastTapTime = now
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                dist = spacing(event)
                if (dist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = ZOOM
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                clampMatrix()
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && mode == DRAG) {
                    matrix.set(savedMatrix)
                    matrix.postTranslate(event.x - start.x, event.y - start.y)
                    clampMatrix()
                }
            }
        }

        imageMatrix = matrix
        invalidate()
        return true
    }

    /** Reset zoom and pan to fit-centre. */
    fun resetZoom() {
        matrix.reset()
        drawable?.let {
            val vw = width.toFloat()
            val vh = height.toFloat()
            val dw = it.intrinsicWidth.toFloat()
            val dh = it.intrinsicHeight.toFloat()
            if (dw <= 0 || dh <= 0) return
            val s = min(vw / dw, vh / dh)
            matrix.postScale(s, s)
            matrix.postTranslate((vw - dw * s) / 2f, (vh - dh * s) / 2f)
        }
        scale = 1f
        imageMatrix = matrix
        invalidate()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { resetZoom() }
    }

    // ── helpers ───────────────────────────────────────────────

    private fun clampMatrix() {
        val values = FloatArray(9)
        matrix.getValues(values)
        val currentScale = values[Matrix.MSCALE_X]

        // Clamp scale
        if (currentScale < minScale) {
            matrix.setScale(minScale, minScale)
        } else if (currentScale > maxScale) {
            val px = values[Matrix.MTRANS_X]
            val py = values[Matrix.MTRANS_Y]
            matrix.setScale(maxScale, maxScale)
            matrix.postTranslate(px, py)
        }
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        point.set((event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val values = FloatArray(9)
            matrix.getValues(values)
            val currentScale = values[Matrix.MSCALE_X]
            val newScale = max(minScale, min(currentScale * scaleFactor, maxScale))
            val ratio = newScale / currentScale
            matrix.postScale(ratio, ratio, detector.focusX, detector.focusY)
            imageMatrix = matrix
            return true
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}

