package com.ibs.ibs_antdrivers.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A custom view that displays confetti animation
 */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val confettiPieces = mutableListOf<ConfettiPiece>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    private val colors = listOf(
        Color.parseColor("#D4AF37"), // Gold
        Color.parseColor("#FFD700"), // Bright Gold
        Color.parseColor("#FFA500"), // Orange
        Color.parseColor("#0A1F44"), // Navy
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#E91E63"), // Pink
        Color.parseColor("#2196F3"), // Blue
        Color.parseColor("#9C27B0"), // Purple
    )

    data class ConfettiPiece(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var size: Float,
        var color: Int,
        var shape: Int, // 0 = rectangle, 1 = circle, 2 = triangle
        var alpha: Float = 1f
    )

    fun startConfetti() {
        confettiPieces.clear()

        val centerX = width / 2f
        val centerY = height / 2f

        // Create confetti pieces
        repeat(80) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 800f + 400f
            val radians = Math.toRadians(angle.toDouble())

            confettiPieces.add(
                ConfettiPiece(
                    x = centerX,
                    y = centerY,
                    velocityX = (cos(radians) * speed).toFloat(),
                    velocityY = (sin(radians) * speed).toFloat() - 200f, // Bias upward
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = Random.nextFloat() * 720f - 360f,
                    size = Random.nextFloat() * 16f + 8f,
                    color = colors.random(),
                    shape = Random.nextInt(3)
                )
            )
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500
            interpolator = AccelerateInterpolator(0.5f)
            addUpdateListener { animation ->
                val deltaTime = 0.016f // ~60fps
                val progress = animation.animatedValue as Float

                confettiPieces.forEach { piece ->
                    piece.x += piece.velocityX * deltaTime
                    piece.y += piece.velocityY * deltaTime
                    piece.velocityY += 1200f * deltaTime // Gravity
                    piece.velocityX *= 0.99f // Air resistance
                    piece.rotation += piece.rotationSpeed * deltaTime

                    // Fade out towards the end
                    if (progress > 0.6f) {
                        piece.alpha = 1f - ((progress - 0.6f) / 0.4f)
                    }
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        confettiPieces.forEach { piece ->
            if (piece.alpha <= 0) return@forEach

            paint.color = piece.color
            paint.alpha = (piece.alpha * 255).toInt()

            canvas.save()
            canvas.translate(piece.x, piece.y)
            canvas.rotate(piece.rotation)

            when (piece.shape) {
                0 -> { // Rectangle
                    canvas.drawRect(
                        -piece.size / 2,
                        -piece.size / 4,
                        piece.size / 2,
                        piece.size / 4,
                        paint
                    )
                }
                1 -> { // Circle
                    canvas.drawCircle(0f, 0f, piece.size / 3, paint)
                }
                2 -> { // Triangle/Star shape
                    val path = android.graphics.Path()
                    val halfSize = piece.size / 2
                    path.moveTo(0f, -halfSize)
                    path.lineTo(halfSize * 0.866f, halfSize * 0.5f)
                    path.lineTo(-halfSize * 0.866f, halfSize * 0.5f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }

            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}

