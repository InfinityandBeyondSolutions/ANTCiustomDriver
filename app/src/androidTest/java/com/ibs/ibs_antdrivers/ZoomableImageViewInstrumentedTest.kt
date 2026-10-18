package com.ibs.ibs_antdrivers

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ibs.ibs_antdrivers.ui.ZoomableImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZoomableImageViewInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun zoomUsesTheFittedPageSizeAndStaysWithinBounds() = withImage { view ->
        assertEquals(1f, view.zoom, 0.001f)
        assertEquals(0.5f, scale(view), 0.001f)
        view.zoomIn()
        assertEquals(1.25f, view.zoom, 0.001f)
        assertEquals(0.625f, scale(view), 0.001f)
        repeat(30) { view.zoomIn() }
        assertEquals(5f, view.zoom, 0.001f)
        repeat(40) { view.zoomOut() }
        assertEquals(1f, view.zoom, 0.001f)
        assertEquals(0.5f, scale(view), 0.001f)
    }

    @Test fun resetAndResizingFitTheWholePage() = withImage { view ->
        repeat(3) { view.zoomIn() }
        view.resetZoom()
        assertEquals(1f, view.zoom, 0.001f)
        view.zoomIn()
        view.layout(0, 0, 900, 600)
        assertEquals(1f, view.zoom, 0.001f)
        assertEquals(600f / 1800, scale(view), 0.001f)
        val values = FloatArray(9).also { view.imageMatrix.getValues(it) }
        assertEquals(250f, values[Matrix.MTRANS_X], 0.01f)
        assertEquals(0f, values[Matrix.MTRANS_Y], 0.01f)
    }

    @Test fun pinchGestureMagnifiesThePage() = withImage { view ->
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, span: Float, pointers: Int = 2, step: Long) {
            val properties = Array(pointers) { index -> MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coordinates = Array(pointers) { index -> MotionEvent.PointerCoords().apply {
                x = 300f + if (index == 0) -span / 2 else span / 2
                y = 450f
                pressure = 1f
                size = 1f
            } }
            val event = MotionEvent.obtain(
                downTime, downTime + step, action, pointers, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            view.onTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 200f, 1, 0)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 200f, step = 20)
        send(MotionEvent.ACTION_MOVE, 300f, step = 40)
        send(MotionEvent.ACTION_MOVE, 450f, step = 60)
        send(MotionEvent.ACTION_MOVE, 600f, step = 80)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 600f, step = 100)
        send(MotionEvent.ACTION_UP, 600f, 1, 120)
        assertTrue("Pinching should magnify the fitted page", view.zoom > 1f)
        assertTrue(view.zoom <= 5f)
    }

    @Test fun doubleTapZoomsAndThenReturnsToFit() = withImage { view ->
        val time = SystemClock.uptimeMillis()
        fun tap(offset: Long) {
            for ((action, delta) in listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 20L)) {
                val event = MotionEvent.obtain(time + offset, time + offset + delta, action, 300f, 450f, 0)
                view.onTouchEvent(event)
                event.recycle()
            }
        }
        tap(0)
        tap(100)
        assertEquals(2f, view.zoom, 0.001f)
        tap(1000)
        tap(1100)
        assertEquals(1f, view.zoom, 0.001f)
    }

    private fun scale(view: ZoomableImageView): Float =
        FloatArray(9).also { view.imageMatrix.getValues(it) }[Matrix.MSCALE_X]

    private fun withImage(test: (ZoomableImageView) -> Unit) {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val bitmap = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
            bitmap.density = context.resources.displayMetrics.densityDpi
            val view = ZoomableImageView(context)
            try {
                view.layout(0, 0, 600, 900)
                view.setImageBitmap(bitmap)
                test(view)
            } finally {
                view.setImageDrawable(null)
                bitmap.recycle()
            }
        }
    }
}
