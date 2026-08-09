package com.ibs.ibs_antdrivers

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs

/**
 * Recognises an inward horizontal swipe from either screen edge as a back gesture.
 * Restricting recognition to the edges preserves normal RecyclerView and carousel swipes.
 */
class EdgeSwipeBackLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var onBackSwipe: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val edgeWidth = 32f * density
    private val triggerDistance = 72f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var startedFromLeft = false
    private var trackingEdgeSwipe = false
    private var gestureTriggered = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startedFromLeft = downX <= edgeWidth
                trackingEdgeSwipe = startedFromLeft || downX >= width - edgeWidth
                gestureTriggered = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!trackingEdgeSwipe || gestureTriggered) {
                    return super.onInterceptTouchEvent(event)
                }

                val dx = event.x - downX
                val dy = event.y - downY
                val movingInward = if (startedFromLeft) dx > 0f else dx < 0f

                if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    trackingEdgeSwipe = false
                } else if (!movingInward && abs(dx) > touchSlop) {
                    trackingEdgeSwipe = false
                } else if (movingInward && abs(dx) >= triggerDistance && abs(dx) > abs(dy) * 1.4f) {
                    gestureTriggered = true
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onBackSwipe?.invoke()
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> resetGesture()
        }

        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gestureTriggered) return super.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetGesture()
        }
        return true
    }

    private fun resetGesture() {
        trackingEdgeSwipe = false
        gestureTriggered = false
    }
}
