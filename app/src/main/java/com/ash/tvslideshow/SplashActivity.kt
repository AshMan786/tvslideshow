package com.ash.tvslideshow

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Cinematic splash screen with elegant staggered animations.
 * Displays for 3 seconds before transitioning to MainActivity.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    private val splashDuration = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Get all animated elements
        val iconView = findViewById<View>(R.id.splash_icon)
        val titleText = findViewById<TextView>(R.id.splash_title)
        val dividerView = findViewById<View>(R.id.splash_divider)
        val subtitleText = findViewById<TextView>(R.id.splash_subtitle)

        // Cinematic staggered entrance animations
        fadeInWithScale(iconView, 0, 900, 0.8f)
        fadeIn(titleText, 350, 700)
        fadeInWithScale(dividerView, 600, 500, 0.3f)
        fadeIn(subtitleText, 800, 600)

        // Navigate to main after delay with smooth transition
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
            interpolator = AccelerateDecelerateInterpolator()
        }
        view.startAnimation(anim)
    }

    private fun fadeInWithScale(view: View, startDelay: Long, duration: Long, startScale: Float) {
        view.alpha = 0f
        view.visibility = View.VISIBLE

        val fadeAnim = AlphaAnimation(0f, 1f).apply {
            this.duration = duration
            fillAfter = true
        }

        val scaleAnim = ScaleAnimation(
            startScale, 1f,
            startScale, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            fillAfter = true
        }

        val animSet = AnimationSet(true).apply {
            addAnimation(fadeAnim)
            addAnimation(scaleAnim)
            this.startOffset = startDelay
            interpolator = AccelerateDecelerateInterpolator()
            fillAfter = true
        }

        view.startAnimation(animSet)
    }
}