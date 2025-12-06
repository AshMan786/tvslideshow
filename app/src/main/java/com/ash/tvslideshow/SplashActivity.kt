package com.ash.tvslideshow

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Splash screen displayed for 3 seconds on app launch.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    private val splashDuration = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Fade in animation
        val titleText = findViewById<TextView>(R.id.splash_title)
        val subtitleText = findViewById<TextView>(R.id.splash_subtitle)
        val iconView = findViewById<View>(R.id.splash_icon)

        fadeIn(iconView, 0, 800)
        fadeIn(titleText, 400, 800)
        fadeIn(subtitleText, 800, 800)

        // Navigate to main after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, splashDuration)
    }

    private fun fadeIn(view: View, startDelay: Long, duration: Long) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        val anim = AlphaAnimation(0f, 1f).apply {
            this.startOffset = startDelay
            this.duration = duration
            fillAfter = true
        }
        view.startAnimation(anim)
    }
}
