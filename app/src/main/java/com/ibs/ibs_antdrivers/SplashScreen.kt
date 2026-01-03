package com.ibs.ibs_antdrivers

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        val animatedLogo = findViewById<ImageView>(R.id.animatedLogo)
        val drawable = animatedLogo.drawable
        if (drawable is Animatable) {
            drawable.start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, SplashscreenTwo::class.java))
            finish()
        }, 2500) // Delay to let the animation play
    }
}