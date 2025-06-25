package com.ibs.ibs_antdrivers

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.jvm.java

class SplashscreenTwo : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashscreen_two)

        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        progressBar.max = 100

        simulateLoading()
    }

    private fun simulateLoading() {
        var progress = 0
        val handler = Handler(Looper.getMainLooper())

        Thread {
            while (progress <= 100) {
                val currentProgress = progress
                handler.post {
                    progressBar.progress = currentProgress
                    progressText.text = "$currentProgress%"
                }
                progress++
                Thread.sleep(20) // 100 * 20ms = ~2 seconds
            }

            handler.post {
                // Fade-out animation for current activity
                val fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
                findViewById<View>(R.id.main).startAnimation(fadeOut)

                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        val intent = Intent(this@SplashscreenTwo, Login::class.java)
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                })
            }
        }.start()
    }
}