package com.ibs.ibs_antdrivers

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.max

class SplashscreenTwo : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private var progressAnimator: ValueAnimator? = null
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashscreen_two)

        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        progressBar.max = 100
        progressBar.progress = 0
        progressText.text = "0%"

        simulateLoading()
    }

    override fun onDestroy() {
        progressAnimator?.cancel()
        progressAnimator = null
        super.onDestroy()
    }

    private fun simulateLoading() {
        // A slightly eased loading animation feels more premium than a rigid 0..100 loop.
        // Total ~2.1s so the whole 2-splash flow stays snappy.
        val totalMs = 2100L

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = totalMs
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                val clamped = max(0, minOf(100, value))
                progressBar.progress = clamped
                progressText.text = "$clamped%"
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isFinishing && !hasNavigated) {
                        navigateToNextScreen()
                    }
                }
            })
            start()
        }
    }

    private fun navigateToNextScreen() {
        if (hasNavigated) return
        hasNavigated = true

        // Check auth status immediately
        val auth = FirebaseAuth.getInstance()
        val isLoggedIn = auth.currentUser != null && SessionPrefs.validateOrClear(this)

        val nextActivity = if (isLoggedIn) {
            MainActivity::class.java
        } else {
            Login::class.java
        }

        // Navigate directly without animation delay
        val intent = Intent(this, nextActivity)
        // Clear the back stack so user can't go back to splash screens
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        // Use simple fade transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}