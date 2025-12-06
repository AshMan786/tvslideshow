package com.ash.tvslideshow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

/**
 * Displays images in a slideshow with fade transitions.
 * - UP: Show/hide controls overlay
 * - LEFT/RIGHT: Previous/next image
 * - CENTER/ENTER: Pause/Resume
 * - BACK: Return to settings
 */
class SlideshowActivity : ComponentActivity() {

    private lateinit var imageView1: ImageView
    private lateinit var imageView2: ImageView
    private lateinit var controlsOverlay: LinearLayout
    private lateinit var imageCountText: TextView
    private lateinit var delayText: TextView
    private lateinit var statusText: TextView

    private var imageFiles: List<File> = emptyList()
    private var currentIndex = 0
    private var isPlaying = true
    private var delayMillis = 5000L
    private var useFirstImageView = true  // Track which ImageView is currently showing

    private val fadeDuration = 800L  // milliseconds

    private val handler = Handler(Looper.getMainLooper())

    private fun scheduleNextImage() {
        if (isPlaying && imageFiles.isNotEmpty()) {
            handler.postDelayed({
                if (isPlaying && imageFiles.isNotEmpty()) {
                    val nextIndex = (currentIndex + 1) % imageFiles.size
                    showImageWithCrossfade(nextIndex)
                }
            }, delayMillis)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during slideshow - prevents sleep and screensaver
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_slideshow)

        imageView1 = findViewById(R.id.slideshow_image1)
        imageView2 = findViewById(R.id.slideshow_image2)
        controlsOverlay = findViewById(R.id.controls_overlay)
        imageCountText = findViewById(R.id.image_count_text)
        delayText = findViewById(R.id.delay_text)
        statusText = findViewById(R.id.slideshow_status_text)

        // Initialize both image views
        imageView1.alpha = 1f
        imageView2.alpha = 0f

        loadSettings()
        loadImages()

        if (imageFiles.isNotEmpty()) {
            // Show first image immediately (no fade)
            showFirstImage()
            startSlideshow()
        } else {
            statusText.text = "No images found"
            statusText.visibility = View.VISIBLE
        }
    }

    private fun loadSettings() {
        val seconds = SlideshowPrefs.getDelaySeconds(this)
        delayMillis = seconds * 1000L
    }

    private fun loadImages() {
        val folderPath = SlideshowPrefs.getFolderPath(this)
        val includeSubfolders = SlideshowPrefs.getIncludeSubfolders(this)
        imageFiles = ImageLoader.getImageFiles(folderPath, includeSubfolders)
    }

    private fun loadBitmap(file: File): Bitmap? {
        return try {
            // Load with inSampleSize for large images to prevent OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Calculate sample size for images larger than 1920x1080
            var sampleSize = 1
            while (options.outWidth / sampleSize > 1920 || options.outHeight / sampleSize > 1080) {
                sampleSize *= 2
            }

            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, finalOptions)
        } catch (e: Exception) {
            null
        }
    }

    private fun showFirstImage() {
        if (imageFiles.isEmpty()) return

        currentIndex = 0
        val file = imageFiles[currentIndex]
        val bitmap = loadBitmap(file)

        if (bitmap != null) {
            imageView1.setImageBitmap(bitmap)
            imageView1.alpha = 1f
            imageView2.alpha = 0f
            useFirstImageView = true
        }

        updateOverlayInfo()
    }

    private fun showImageWithCrossfade(index: Int) {
        if (imageFiles.isEmpty()) return

        currentIndex = index.coerceIn(0, imageFiles.lastIndex)
        val file = imageFiles[currentIndex]
        val bitmap = loadBitmap(file)

        if (bitmap != null) {
            // Determine which views to use
            val showingView: ImageView
            val hiddenView: ImageView

            if (useFirstImageView) {
                showingView = imageView1
                hiddenView = imageView2
            } else {
                showingView = imageView2
                hiddenView = imageView1
            }

            // Set the new image on the hidden view
            hiddenView.setImageBitmap(bitmap)

            // Cancel any ongoing animations
            showingView.animate().cancel()
            hiddenView.animate().cancel()

            // Crossfade: fade in hidden view, fade out showing view
            hiddenView.alpha = 0f
            hiddenView.animate()
                .alpha(1f)
                .setDuration(fadeDuration)
                .withEndAction {
                    // Schedule next image AFTER fade completes for consistent timing
                    scheduleNextImage()
                }
                .start()

            showingView.animate()
                .alpha(0f)
                .setDuration(fadeDuration)
                .start()

            // Swap which view is "active"
            useFirstImageView = !useFirstImageView
        }

        updateOverlayInfo()
    }

    private fun showNextImage() {
        if (imageFiles.isEmpty()) return
        val nextIndex = (currentIndex + 1) % imageFiles.size
        showImageWithCrossfade(nextIndex)
    }

    private fun showPreviousImage() {
        if (imageFiles.isEmpty()) return
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else imageFiles.lastIndex
        showImageWithCrossfade(prevIndex)
    }

    private fun startSlideshow() {
        isPlaying = true
        loadSettings()  // Reload in case settings changed
        handler.removeCallbacksAndMessages(null)  // Clear all pending callbacks
        scheduleNextImage()
        updateOverlayInfo()
    }

    private fun stopSlideshow() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)  // Clear all pending callbacks
        updateOverlayInfo()
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    private fun toggleControlsOverlay() {
        controlsOverlay.visibility = if (controlsOverlay.visibility == View.VISIBLE) {
            View.GONE
        } else {
            updateOverlayInfo()
            View.VISIBLE
        }
    }

    private fun updateOverlayInfo() {
        imageCountText.text = "Image ${currentIndex + 1} of ${imageFiles.size}"
        delayText.text = "Delay: ${delayMillis / 1000}s"
        statusText.text = if (isPlaying) "Playing" else "Paused"
        statusText.visibility = View.VISIBLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                toggleControlsOverlay()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                handler.removeCallbacksAndMessages(null)  // Cancel pending transitions
                showPreviousImage()
                // Note: scheduleNextImage is called by showImageWithCrossfade after fade ends
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handler.removeCallbacksAndMessages(null)  // Cancel pending transitions
                showNextImage()
                // Note: scheduleNextImage is called by showImageWithCrossfade after fade ends
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                togglePlayPause()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
        if (isPlaying && imageFiles.isNotEmpty()) {
            scheduleNextImage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        imageView1.animate().cancel()
        imageView2.animate().cancel()
        imageView1.setImageBitmap(null)
        imageView2.setImageBitmap(null)
    }
}
