package com.ibs.ibs_antdrivers.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/** Pinch, pan and button zoom from the fitted page size up to 5x. Double-tap zooms or fits. */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private val transform = Matrix()
    var zoom = 1f
        private set
    var onZoomChanged: ((Float) -> Unit)? = null

    private val contentWidth get() = (width - paddingLeft - paddingRight).toFloat()
    private val contentHeight get() = (height - paddingTop - paddingBottom).toFloat()

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomTo(zoom * detector.scaleFactor, detector.focusX - paddingLeft, detector.focusY - paddingTop)
                return true
            }
        }
    )
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true

            override fun onScroll(
                first: MotionEvent?, event: MotionEvent, distanceX: Float, distanceY: Float,
            ): Boolean {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    transform.postTranslate(-distanceX, -distanceY)
                    constrainPan()
                    imageMatrix = transform
                }
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (zoom > 1.01f) resetZoom()
                else zoomTo(2f, event.x - paddingLeft, event.y - paddingTop)
                return true
            }

            override fun onSingleTapConfirmed(event: MotionEvent) = performClick()
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = transform
    }

    fun zoomIn() = zoomTo(zoom * 1.25f)
    fun zoomOut() = zoomTo(zoom / 1.25f)

    private fun zoomTo(value: Float, focusX: Float = contentWidth / 2, focusY: Float = contentHeight / 2) {
        if (drawable == null || contentWidth <= 0 || contentHeight <= 0) return
        val next = value.coerceIn(1f, 5f)
        val factor = next / zoom
        transform.postScale(factor, factor, focusX, focusY)
        zoom = next
        constrainPan()
        imageMatrix = transform
        onZoomChanged?.invoke(zoom)
    }

    /** 100% means the whole page fits, even when the PDF is larger than the screen. */
    fun resetZoom() {
        val image = drawable ?: return
        if (contentWidth <= 0 || contentHeight <= 0 || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val fit = min(contentWidth / image.intrinsicWidth, contentHeight / image.intrinsicHeight)
        transform.setScale(fit, fit)
        zoom = 1f
        constrainPan()
        imageMatrix = transform
        onZoomChanged?.invoke(zoom)
    }

    private fun constrainPan() {
        val image = drawable ?: return
        val values = FloatArray(9)
        transform.getValues(values)
        val imageWidth = image.intrinsicWidth * values[Matrix.MSCALE_X]
        val imageHeight = image.intrinsicHeight * values[Matrix.MSCALE_Y]
        val x = if (imageWidth <= contentWidth) (contentWidth - imageWidth) / 2
            else values[Matrix.MTRANS_X].coerceIn(contentWidth - imageWidth, 0f)
        val y = if (imageHeight <= contentHeight) (contentHeight - imageHeight) / 2
            else values[Matrix.MTRANS_Y].coerceIn(contentHeight - imageHeight, 0f)
        transform.postTranslate(x - values[Matrix.MTRANS_X], y - values[Matrix.MTRANS_Y])
    }

    override fun setImageBitmap(bitmap: Bitmap?) {
        super.setImageBitmap(bitmap)
        resetZoom()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        resetZoom()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // A PDF pan must not trigger the activity's swipe-back gesture.
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
